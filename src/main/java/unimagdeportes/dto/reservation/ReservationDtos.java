package unimagdeportes.dto.reservation;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import unimagdeportes.model.reservation.ReservationStatus;

public final class ReservationDtos {
    private ReservationDtos() { }

    public record Request(UUID spaceId, OffsetDateTime startAt, Integer slots, List<EquipmentRequest> equipment) { }
    public record EquipmentRequest(UUID equipmentId, Integer quantity) { }
    public record Space(UUID id, String name) { }
    public record Equipment(UUID equipmentId, String name, int quantity) { }
    public record Response(UUID id, Space space, OffsetDateTime startAt, OffsetDateTime endAt,
            ReservationStatus status, OffsetDateTime createdAt, OffsetDateTime checkedInAt, List<Equipment> equipment) {
        public Response { equipment = List.copyOf(equipment); }
    }
}
