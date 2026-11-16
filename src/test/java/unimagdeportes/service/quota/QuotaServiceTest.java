package unimagdeportes.service.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import unimagdeportes.mapper.quota.QuotaMapperImpl;
import unimagdeportes.model.reservation.ReservationStatus;
import unimagdeportes.service.parameter.ParameterService;
import unimagdeportes.service.reservation.ReservationService;

class QuotaServiceTest {
    private static final Instant WEEK_START = Instant.parse("2026-11-09T05:00:00Z");
    private static final Instant WEEK_END = WEEK_START.plus(Duration.ofDays(7));
    private final ReservationService reservations = mock(ReservationService.class);
    private final ParameterService parameters = mock(ParameterService.class);
    private final UUID userId = UUID.randomUUID();

    @Test
    void countsOnlyReservationTimeWithinTheWeek() {
        when(reservations.overlappingQuotaReservations(userId, WEEK_START, WEEK_END))
                .thenReturn(List.of(reservation(WEEK_START.minus(Duration.ofHours(1)), WEEK_START.plus(Duration.ofHours(1)))));

        assertThat(service(WEEK_START).usedInWeek(userId, WEEK_START, WEEK_END)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void currentWeekReportsLimitUsedAvailableAndResetAtNextMonday() {
        when(parameters.current()).thenReturn(new ParameterService.CurrentParameters(4, 3));
        when(reservations.overlappingQuotaReservations(userId, WEEK_START, WEEK_END))
                .thenReturn(List.of(reservation(WEEK_START.plus(Duration.ofHours(8)), WEEK_START.plus(Duration.ofHours(11)))));

        var quota = service(Instant.parse("2026-11-15T23:59:00-05:00")).currentWeek(userId);

        assertThat(quota.weekStart().toString()).isEqualTo("2026-11-09T00:00-05:00");
        assertThat(quota.weekEnd().toString()).isEqualTo("2026-11-16T00:00-05:00");
        assertThat(quota.resetAt()).isEqualTo(quota.weekEnd());
        assertThat(quota.limitHours()).isEqualTo(4);
        assertThat(quota.usedHours()).isEqualTo(3);
        assertThat(quota.availableHours()).isEqualTo(1);
    }

    @Test
    void availableHoursNeverGoBelowZeroWhenTheLimitWasReduced() {
        when(parameters.current()).thenReturn(new ParameterService.CurrentParameters(1, 3));
        when(reservations.overlappingQuotaReservations(userId, WEEK_START, WEEK_END))
                .thenReturn(List.of(reservation(WEEK_START.plus(Duration.ofHours(8)), WEEK_START.plus(Duration.ofHours(10)))));

        var quota = service(WEEK_START).currentWeek(userId);

        assertThat(quota.usedHours()).isEqualTo(2);
        assertThat(quota.availableHours()).isZero();
    }

    private QuotaService service(Instant now) {
        return new QuotaService(reservations, parameters, new QuotaMapperImpl(),
                Clock.fixed(now, ZoneId.of("America/Bogota")));
    }

    private ReservationService.ReservationInfo reservation(Instant start, Instant end) {
        return new ReservationService.ReservationInfo(UUID.randomUUID(), userId, UUID.randomUUID(), start, end,
                ReservationStatus.CONFIRMED);
    }
}
