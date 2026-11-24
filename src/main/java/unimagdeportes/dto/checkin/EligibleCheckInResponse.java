package unimagdeportes.dto.checkin;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EligibleCheckInResponse(UUID reservationId, UUID spaceId, OffsetDateTime startAt,
        OffsetDateTime endAt, OffsetDateTime opensAt, OffsetDateTime closesAt) { }
