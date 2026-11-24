package unimagdeportes.service.equipment;

import java.util.UUID;
import java.time.Instant;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.dto.equipment.EquipmentDtos;
import unimagdeportes.mapper.equipment.EquipmentMapper;
import unimagdeportes.model.equipment.Equipment;
import unimagdeportes.model.equipment.EquipmentStatus;
import unimagdeportes.model.space.Discipline;
import unimagdeportes.repository.equipment.EquipmentRepository;
import unimagdeportes.service.reservation.ReservationLock;

@Service
public class EquipmentService {
    private final EquipmentRepository equipment;
    private final JdbcTemplate jdbc;
    private final ReservationLock lock;
    private final Clock clock;
    private final EquipmentMapper mapper;

    public EquipmentService(EquipmentRepository equipment, JdbcTemplate jdbc, ReservationLock lock,
            Clock clock, EquipmentMapper mapper) {
        this.equipment = equipment;
        this.jdbc = jdbc;
        this.lock = lock;
        this.clock = clock;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<EquipmentDtos.Info> list(Discipline discipline) {
        return equipment.findAll().stream()
                .filter(item -> discipline == null || item.getDiscipline() == discipline)
                .map(mapper::toInfo).toList();
    }

    @Transactional(readOnly = true)
    public EquipmentDtos.Availability availability(OffsetDateTime start, OffsetDateTime end, Discipline discipline) {
        if (!end.isAfter(start)) {
            throw invalid("endAt", "debe ser mayor que startAt");
        }
        var items = equipment.findAll().stream()
                .filter(item -> item.getStatus() == EquipmentStatus.AVAILABLE)
                .filter(item -> discipline == null || item.getDiscipline() == discipline)
                .map(item -> {
                    int committed = committed(item.getId(), start.toInstant(), end.toInstant());
                    return new EquipmentDtos.AvailabilityItem(item.getId(), item.getName(), item.getStock(),
                            committed, Math.max(0, item.getStock() - committed));
                })
                .filter(item -> item.available() > 0)
                .toList();
        var zone = ZoneId.of("America/Bogota");
        return new EquipmentDtos.Availability(start.atZoneSameInstant(zone).toOffsetDateTime(),
                end.atZoneSameInstant(zone).toOffsetDateTime(), items);
    }

    @Transactional
    public EquipmentDtos.Info create(EquipmentDtos.Input input) {
        lock.acquire();
        UUID id = UUID.randomUUID();
        validate(input.name(), input.stock(), id);
        var item = new Equipment(id, input.name(), input.discipline(), input.stock(),
                input.status() == null ? EquipmentStatus.AVAILABLE : input.status());
        return mapper.toInfo(equipment.save(item));
    }

    @Transactional
    public EquipmentDtos.Info update(UUID id, EquipmentDtos.Update input) {
        lock.acquire();
        var item = equipment.findForUpdate(id).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND", "El implemento no existe."));
        String name = input.isNamePresent() ? input.getName() : item.getName();
        Integer stock = input.isStockPresent() ? input.getStock() : Integer.valueOf(item.getStock());
        validate(name, stock, id);
        if (input.isStatusPresent() && input.getStatus() == null) {
            throw invalid("status", "debe ser un estado válido");
        }
        if (stock < item.getStock()) {
            int futureCommitted = committed(id, clock.instant(), Instant.parse("9999-12-31T23:59:59Z"));
            if (stock < futureCommitted) {
                throw new BusinessException(HttpStatus.CONFLICT, "STOCK_BELOW_COMMITTED",
                        "El stock no puede ser inferior al compromiso simultáneo futuro.");
            }
        }
        item.update(name, input.isDisciplinePresent() ? input.getDiscipline() : item.getDiscipline(), stock,
                input.isStatusPresent() ? input.getStatus() : item.getStatus());
        return mapper.toInfo(item);
    }

    private void validate(String name, Integer stock, UUID id) {
        if (name == null || name.isBlank() || name.length() > 200) {
            throw invalid("name", "debe contener entre 1 y 200 caracteres");
        }
        if (stock == null || stock < 0) {
            throw invalid("stock", "debe ser mayor o igual a 0");
        }
        if (equipment.existsByNameAndIdNot(name, id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "EQUIPMENT_NAME_ALREADY_EXISTS",
                    "Ya existe un implemento con ese nombre.");
        }
    }

    private BusinessException invalid(String field, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "La solicitud contiene campos inválidos.", Map.of("fields", Map.of(field, message)));
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
            int committed = committed(request.equipmentId(), start, end);
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

    private int committed(UUID equipmentId, Instant start, Instant end) {
        return jdbc.queryForObject("""
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
                    """, Integer.class, Timestamp.from(start), equipmentId, Timestamp.from(end),
                    Timestamp.from(start), Timestamp.from(end), equipmentId, Timestamp.from(end),
                    Timestamp.from(start));
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
