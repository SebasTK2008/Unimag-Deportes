package unimagdeportes.service.equipment;

import java.util.UUID;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.model.equipment.EquipmentStatus;
import unimagdeportes.model.space.Discipline;
import unimagdeportes.repository.equipment.EquipmentRepository;

@Service
public class EquipmentService {
    private final EquipmentRepository equipment;
    private final JdbcTemplate jdbc;

    public EquipmentService(EquipmentRepository equipment, JdbcTemplate jdbc) {
        this.equipment = equipment;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public EquipmentInfo get(UUID id) {
        var item = equipment.findById(id).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND", "El implemento no existe."));
        return new EquipmentInfo(item.getId(), item.getName(), item.getDiscipline(), item.getStock(), item.getStatus());
    }

    public record EquipmentInfo(UUID id, String name, Discipline discipline, int stock, EquipmentStatus status) { }

    public record RequestedEquipment(UUID equipmentId, int quantity) { }
    public record ReservedEquipment(UUID equipmentId, String name, int quantity) { }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public List<ReservedEquipment> validateForReservation(List<RequestedEquipment> requested, Instant start, Instant end) {
        var sorted = requested.stream().sorted(java.util.Comparator.comparing(RequestedEquipment::equipmentId)).toList();
        var result = new java.util.ArrayList<ReservedEquipment>();
        for (var request : sorted) {
            var item = equipment.findForUpdate(request.equipmentId()).orElseThrow(() ->
                    new BusinessException(HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND", "El implemento no existe."));
            Integer committed = jdbc.queryForObject("""
                    with changes as (
                        select greatest(r.start_at, ?) as at, re.quantity as change
                        from reservation_equipment re join reservation r on r.id = re.reservation_id
                        where re.equipment_id = ? and r.status in ('CONFIRMED','IN_USE','COMPLETED','NO_SHOW')
                          and r.start_at < ? and r.end_at > ?
                        union all
                        select least(r.end_at, ?) as at, -re.quantity as change
                        from reservation_equipment re join reservation r on r.id = re.reservation_id
                        where re.equipment_id = ? and r.status in ('CONFIRMED','IN_USE','COMPLETED','NO_SHOW')
                          and r.start_at < ? and r.end_at > ?
                    ), points as (
                        select at, sum(change) as change from changes group by at
                    ), totals as (
                        select sum(change) over (order by at) as committed from points
                    )
                    select coalesce(max(committed), 0) from totals
                    """, Integer.class, Timestamp.from(start), request.equipmentId(), Timestamp.from(end),
                    Timestamp.from(start), Timestamp.from(end), request.equipmentId(), Timestamp.from(end),
                    Timestamp.from(start));
            int available = Math.max(0, item.getStock() - committed);
            if (item.getStatus() != EquipmentStatus.AVAILABLE || request.quantity() > available) {
                throw new BusinessException(HttpStatus.CONFLICT, "EQUIPMENT_UNAVAILABLE",
                        "No hay suficientes unidades de " + item.getName() + " para esa franja.",
                        Map.of("equipmentId", item.getId(), "requested", request.quantity(), "available", available));
            }
            result.add(new ReservedEquipment(item.getId(), item.getName(), request.quantity()));
        }
        return List.copyOf(result);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void attach(UUID reservationId, List<ReservedEquipment> items) {
        for (var item : items) {
            jdbc.update("insert into reservation_equipment (reservation_id, equipment_id, quantity) values (?, ?, ?)",
                    reservationId, item.equipmentId(), item.quantity());
        }
    }

    /** Implementos solicitados por una reserva, conservados como historial aunque la reserva se cancele. */
    @Transactional(readOnly = true)
    public List<ReservedEquipment> forReservation(UUID reservationId) {
        return jdbc.query("""
                select e.id, e.name, re.quantity
                from reservation_equipment re join equipment e on e.id = re.equipment_id
                where re.reservation_id = ?
                order by e.id
                """, (rs, rowNum) -> new ReservedEquipment(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getInt("quantity")),
                reservationId);
    }
}
