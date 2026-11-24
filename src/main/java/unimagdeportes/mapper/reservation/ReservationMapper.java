package unimagdeportes.mapper.reservation;

import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Component;
import unimagdeportes.dto.reservation.ReservationDtos;
import unimagdeportes.model.reservation.Reservation;
import unimagdeportes.service.equipment.EquipmentService.ReservedEquipment;

/**
 * Convierte la reserva y su detalle en los DTO de UC03/UC04. La fecha se expone
 * en America/Bogota (RNF-07). No usa MapStruct porque el mapeo combina varias
 * fuentes (reserva, nombre del espacio e implementos) con conversión de zona horaria.
 */
@Component
public class ReservationMapper {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    public ReservationDtos.Response toResponse(Reservation reservation, String spaceName,
            List<ReservedEquipment> equipment) {
        return new ReservationDtos.Response(
                reservation.getId(),
                new ReservationDtos.Space(reservation.getSpaceId(), spaceName),
                reservation.getStartAt().atZone(BOGOTA).toOffsetDateTime(),
                reservation.getEndAt().atZone(BOGOTA).toOffsetDateTime(),
                reservation.getStatus(),
                reservation.getCreatedAt().atZone(BOGOTA).toOffsetDateTime(),
                reservation.getCheckedInAt() == null ? null
                        : reservation.getCheckedInAt().atZone(BOGOTA).toOffsetDateTime(),
                equipment.stream().map(this::toEquipment).toList());
    }

    public ReservationDtos.Equipment toEquipment(ReservedEquipment item) {
        return new ReservationDtos.Equipment(item.equipmentId(), item.name(), item.quantity());
    }
}
