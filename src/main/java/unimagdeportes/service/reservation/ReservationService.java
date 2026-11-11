package unimagdeportes.service.reservation;

import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.model.reservation.ReservationStatus;
import unimagdeportes.repository.reservation.ReservationRepository;

@Service
public class ReservationService {
    private final ReservationRepository reservations;

    public ReservationService(ReservationRepository reservations) { this.reservations = reservations; }

    @Transactional(readOnly = true)
    public ReservationInfo get(UUID id) {
        var reservation = reservations.findById(id).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "La reserva no existe."));
        return new ReservationInfo(reservation.getId(), reservation.getUserId(), reservation.getSpaceId(),
                reservation.getStartAt(), reservation.getEndAt(), reservation.getStatus());
    }

    public record ReservationInfo(UUID id, UUID userId, UUID spaceId, Instant startAt, Instant endAt,
            ReservationStatus status) { }
}
