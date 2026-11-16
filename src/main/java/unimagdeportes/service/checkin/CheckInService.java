package unimagdeportes.service.checkin;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.dto.checkin.CheckInResponse;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.mapper.checkin.CheckInMapper;
import unimagdeportes.model.reservation.Reservation;
import unimagdeportes.model.reservation.ReservationStatus;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.policy.CheckInWindowPolicy;
import unimagdeportes.service.reservation.ReservationLock;
import unimagdeportes.service.reservation.ReservationService;

@Service
public class CheckInService {
    private final ReservationLock lock;
    private final ReservationService reservations;
    private final CheckInWindowPolicy window;
    private final AuditService audit;
    private final CheckInMapper mapper;
    private final Clock clock;

    public CheckInService(ReservationLock lock, ReservationService reservations, CheckInWindowPolicy window,
            AuditService audit, CheckInMapper mapper, Clock clock) {
        this.lock = lock;
        this.reservations = reservations;
        this.window = window;
        this.audit = audit;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public CheckInResponse checkIn(UUID spaceId, UUID userId) {
        lock.acquire();
        Instant now = clock.instant();
        if (reservations.hasOtherUserInProgress(spaceId, userId, now)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "La reserva pertenece a otro usuario.");
        }
        List<Reservation> candidates = reservations.checkInCandidates(spaceId, userId);
        Reservation reservation = select(candidates, now);
        if (reservation.getStatus() == ReservationStatus.IN_USE) return mapper.toResponse(reservation);
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            if (reservation.getStatus().name().startsWith("CANCELLED_")) {
                throw new BusinessException(HttpStatus.CONFLICT, "RESERVATION_CANCELLED", "La reserva fue cancelada.");
            }
            throw new BusinessException(HttpStatus.CONFLICT, "CHECKIN_WINDOW_CLOSED", "La ventana de check-in ya cerró.");
        }
        Instant opensAt = window.opensAt(reservation.getStartAt(), reservation.getCheckinOpeningMinutes());
        if (now.isBefore(opensAt)) {
            throw new BusinessException(HttpStatus.CONFLICT, "CHECKIN_TOO_EARLY",
                    "El check-in aún no está disponible.",
                    Map.of("opensAt", opensAt.atZone(ZoneId.of("America/Bogota")).toOffsetDateTime()));
        }
        if (!window.isOpen(now, reservation.getStartAt(), reservation.getEndAt(),
                reservation.getCheckinOpeningMinutes())) {
            throw new BusinessException(HttpStatus.CONFLICT, "CHECKIN_WINDOW_CLOSED", "La ventana de check-in ya cerró.");
        }
        reservation.checkIn(now.truncatedTo(ChronoUnit.MICROS));
        audit.record("CHECK_IN", "RESERVATION", reservation.getId(), userId);
        return mapper.toResponse(reservation);
    }

    private Reservation select(List<Reservation> candidates, Instant now) {
        return candidates.stream()
                .filter(r -> (r.getStatus() == ReservationStatus.CONFIRMED || r.getStatus() == ReservationStatus.IN_USE)
                        && r.getEndAt().isAfter(now))
                .findFirst()
                .orElseGet(() -> candidates.stream().filter(r -> !r.getStartAt().isBefore(now)).findFirst()
                        .orElseGet(() -> candidates.isEmpty() ? noReservation() : candidates.getLast()));
    }

    private Reservation noReservation() {
        throw new BusinessException(HttpStatus.NOT_FOUND, "NO_ACTIVE_RESERVATION",
                "No tiene una reserva activa en este espacio.");
    }
}
