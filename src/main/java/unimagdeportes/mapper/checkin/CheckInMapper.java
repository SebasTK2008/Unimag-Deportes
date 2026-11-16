package unimagdeportes.mapper.checkin;

import java.time.ZoneId;
import org.springframework.stereotype.Component;
import unimagdeportes.dto.checkin.CheckInResponse;
import unimagdeportes.model.reservation.Reservation;

@Component
public class CheckInMapper {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    public CheckInResponse toResponse(Reservation reservation) {
        return new CheckInResponse(reservation.getId(), reservation.getSpaceId(), reservation.getStatus(),
                reservation.getCheckedInAt().atZone(BOGOTA).toOffsetDateTime());
    }
}
