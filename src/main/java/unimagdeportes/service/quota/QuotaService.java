package unimagdeportes.service.quota;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.service.policy.QuotaPolicy;
import unimagdeportes.service.reservation.ReservationService;

@Service
public class QuotaService {
    private final ReservationService reservations;
    private final QuotaPolicy policy = new QuotaPolicy();

    public QuotaService(ReservationService reservations) {
        this.reservations = reservations;
    }

    @Transactional(readOnly = true)
    public Duration usedInWeek(UUID userId, Instant weekStart, Instant weekEnd) {
        var intervals = reservations.overlappingQuotaReservations(userId, weekStart, weekEnd).stream()
                .map(reservation -> new QuotaPolicy.Interval(reservation.startAt(), reservation.endAt()))
                .toList();
        return policy.usedInWeek(intervals, weekStart, weekEnd);
    }
}
