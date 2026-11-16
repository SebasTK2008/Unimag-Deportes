package unimagdeportes.dto.checkin;

import java.time.OffsetDateTime;
import java.util.UUID;
import unimagdeportes.model.reservation.ReservationStatus;

public record CheckInResponse(UUID reservationId, UUID spaceId, ReservationStatus status,
        OffsetDateTime checkedInAt) { }
