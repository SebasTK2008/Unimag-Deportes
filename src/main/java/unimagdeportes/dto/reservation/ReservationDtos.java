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

    public record MineResponse(List<Response> reservations) {
        public MineResponse { reservations = List.copyOf(reservations); }
    }

    public record CancellationPreview(UUID reservationId, boolean late, int hoursCounted, String message) { }

    public record CancellationResult(UUID id, ReservationStatus status, OffsetDateTime cancelledAt,
            int hoursCounted, List<Equipment> equipment) {
        public CancellationResult { equipment = List.copyOf(equipment); }
    }
}
