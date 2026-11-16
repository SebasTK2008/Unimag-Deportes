package unimagdeportes.service.quota;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.dto.quota.QuotaDtos.WeeklyQuota;
import unimagdeportes.mapper.quota.QuotaMapper;
import unimagdeportes.service.parameter.ParameterService;
import unimagdeportes.service.policy.QuotaPolicy;
import unimagdeportes.service.policy.WeekCalculator;
import unimagdeportes.service.reservation.ReservationService;

/**
 * Cuota semanal derivada de las reservas (D-06): no hay saldo persistido ni job de reinicio.
 * El reinicio (UC08-H2) es el resultado de calcular sobre la semana vigente según el {@link Clock}.
 */
@Service
public class QuotaService {
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private final ReservationService reservations;
    private final ParameterService parameters;
    private final QuotaMapper mapper;
    private final Clock clock;
    private final QuotaPolicy policy = new QuotaPolicy();
    private final WeekCalculator weeks = new WeekCalculator();

    public QuotaService(ReservationService reservations, ParameterService parameters, QuotaMapper mapper,
            Clock clock) {
        this.reservations = reservations;
        this.parameters = parameters;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Duration usedInWeek(UUID userId, Instant weekStart, Instant weekEnd) {
        var intervals = reservations.overlappingQuotaReservations(userId, weekStart, weekEnd).stream()
                .map(reservation -> new QuotaPolicy.Interval(reservation.startAt(), reservation.endAt()))
                .toList();
        return policy.usedInWeek(intervals, weekStart, weekEnd);
    }

    /** Cuota de la semana en curso (lunes 00:00 America/Bogota); se reinicia el lunes siguiente. */
    @Transactional(readOnly = true)
    public WeeklyQuota currentWeek(UUID userId) {
        LocalDate today = LocalDate.now(clock.withZone(BOGOTA));
        Instant weekStart = weeks.startOfWeek(today).toInstant();
        Instant weekEnd = weeks.nextWeek(today).toInstant();
        Duration limit = Duration.ofHours(parameters.current().weeklyQuotaHours());
        Duration used = usedInWeek(userId, weekStart, weekEnd);
        return mapper.toWeeklyQuota(new WeekSnapshot(weekStart, weekEnd, limit, used,
                policy.available(limit, used), weekEnd));
    }

    public record WeekSnapshot(Instant weekStart, Instant weekEnd, Duration limit, Duration used,
            Duration available, Instant resetAt) { }
}
