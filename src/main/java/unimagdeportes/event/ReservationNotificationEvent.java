package unimagdeportes.event;

import java.time.Instant;
import java.util.UUID;
import unimagdeportes.model.reservation.ReservationStatus;

public record ReservationNotificationEvent(Type type, UUID reservationId, String recipient, String userName,
        String spaceName, Instant startAt, String reason, Instant suspendedUntil, CancellationDetails cancellation) {
    public ReservationNotificationEvent(Type type, UUID reservationId, String recipient, String userName,
            String spaceName, Instant startAt, String reason, Instant suspendedUntil) {
        this(type, reservationId, recipient, userName, spaceName, startAt, reason, suspendedUntil, null);
    }

    public record CancellationDetails(ReservationStatus status, int reservedHours) { }
    public enum Type { REMINDER, CANCELLATION, BLOCK, NO_SHOW, SUSPENSION }
}
