package unimagdeportes.event;

import java.time.Instant;
import java.util.UUID;

public record ReservationNotificationEvent(Type type, UUID reservationId, String recipient, String userName,
        String spaceName, Instant startAt, String reason, Instant suspendedUntil) {
    public enum Type { REMINDER, CANCELLATION, BLOCK, NO_SHOW, SUSPENSION }
}
