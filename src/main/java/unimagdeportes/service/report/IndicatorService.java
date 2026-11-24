package unimagdeportes.service.report;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.dto.report.ReportDtos.Indicators;
import unimagdeportes.dto.report.ReportDtos.Period;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.mapper.report.ReportMapper;
import unimagdeportes.model.reservation.ReservationStatus;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.policy.WeekCalculator;
import unimagdeportes.service.reservation.ReservationService;

/**
 * Indicadores de uso del período en curso {@code [from, to)} en America/Bogota (UC09-H2, OQ-08),
 * calculados sobre la auditoría (RNF-003):
 * <ul>
 *   <li>{@code reservations}: {@code RESERVATION_CREATED} registradas en el período.</li>
 *   <li>{@code lateCancellations}: {@code RESERVATION_CANCELLED} registradas en el período cuya reserva
 *       quedó en {@code CANCELLED_LATE}; la auditoría no distingue el tipo de cancelación.</li>
 *   <li>{@code noShows}: {@code NO_SHOW_REGISTERED} registradas en el período.</li>
 *   <li>{@code suspendedUsers}: usuarios distintos cuya suspensión se solapa con el período.</li>
 * </ul>
 */
@Service
public class IndicatorService {
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private final AuditService audit;
    private final ReservationService reservations;
    private final ReportMapper mapper;
    private final Clock clock;
    private final WeekCalculator weeks = new WeekCalculator();

    public IndicatorService(AuditService audit, ReservationService reservations,
            ReportMapper mapper, Clock clock) {
        this.audit = audit;
        this.reservations = reservations;
        this.mapper = mapper;
        this.clock = clock;
    }

    public record IndicatorSnapshot(Period period, Instant from, Instant to, long reservations,
            long lateCancellations, long noShows, long suspendedUsers) { }

    @Transactional(readOnly = true)
    public Indicators indicators(String requestedPeriod) {
        Period period = period(requestedPeriod);
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        ZonedDateTime start = period == Period.WEEKLY
                ? weeks.startOfWeek(today) : today.withDayOfMonth(1).atStartOfDay(ZONE);
        Instant from = start.toInstant();
        Instant to = (period == Period.WEEKLY ? start.plusWeeks(1) : start.plusMonths(1)).toInstant();
        long lateCancellations = reservations.countWithStatus(
                audit.distinctEntities("RESERVATION_CANCELLED", from, to), ReservationStatus.CANCELLED_LATE);
        return mapper.toIndicators(new IndicatorSnapshot(period, from, to,
                audit.count("RESERVATION_CREATED", from, to), lateCancellations,
                audit.count("NO_SHOW_REGISTERED", from, to),
                audit.countSuspendedUsers(from, to)));
    }

    private Period period(String value) {
        try {
            return Period.valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD",
                    "El período debe ser WEEKLY o MONTHLY.", Map.of("field", "period"));
        }
    }
}
