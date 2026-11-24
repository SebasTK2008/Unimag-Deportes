package unimagdeportes.mapper.checkin;

import java.time.ZoneId;
import org.springframework.stereotype.Component;
import unimagdeportes.dto.checkin.CheckInResponse;
import unimagdeportes.dto.checkin.EligibleCheckInResponse;
import unimagdeportes.service.policy.CheckInWindowPolicy;
import unimagdeportes.service.reservation.ReservationService.CheckInCandidate;

@Component
public class CheckInMapper {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    public CheckInResponse toResponse(CheckInCandidate reservation) {
        return new CheckInResponse(reservation.id(), reservation.spaceId(), reservation.status(),
                reservation.checkedInAt().atZone(BOGOTA).toOffsetDateTime());
    }

    public EligibleCheckInResponse toEligibleResponse(CheckInCandidate reservation, CheckInWindowPolicy window) {
        return new EligibleCheckInResponse(reservation.id(), reservation.spaceId(),
                reservation.startAt().atZone(BOGOTA).toOffsetDateTime(),
                reservation.endAt().atZone(BOGOTA).toOffsetDateTime(),
                window.opensAt(reservation.startAt(), reservation.checkinOpeningMinutes())
                        .atZone(BOGOTA).toOffsetDateTime(),
                window.closesAt(reservation.endAt()).atZone(BOGOTA).toOffsetDateTime());
    }
}
