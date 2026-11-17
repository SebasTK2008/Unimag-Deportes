package unimagdeportes.mapper.checkin;

import java.time.ZoneId;
import org.springframework.stereotype.Component;
import unimagdeportes.dto.checkin.CheckInResponse;
import unimagdeportes.service.reservation.ReservationService.CheckInCandidate;

@Component
public class CheckInMapper {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    public CheckInResponse toResponse(CheckInCandidate reservation) {
        return new CheckInResponse(reservation.id(), reservation.spaceId(), reservation.status(),
                reservation.checkedInAt().atZone(BOGOTA).toOffsetDateTime());
    }
}
