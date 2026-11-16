package unimagdeportes.service.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import unimagdeportes.model.reservation.ReservationStatus;
import unimagdeportes.service.reservation.ReservationService;

class QuotaServiceTest {
    @Test
    void countsOnlyReservationTimeWithinTheWeek() {
        var reservations = mock(ReservationService.class);
        var userId = UUID.randomUUID();
        var weekStart = Instant.parse("2026-11-09T05:00:00Z");
        var weekEnd = weekStart.plus(Duration.ofDays(7));
        var reservation = new ReservationService.ReservationInfo(UUID.randomUUID(), userId, UUID.randomUUID(),
                weekStart.minus(Duration.ofHours(1)), weekStart.plus(Duration.ofHours(1)),
                ReservationStatus.CONFIRMED);
        when(reservations.overlappingQuotaReservations(userId, weekStart, weekEnd)).thenReturn(List.of(reservation));

        assertThat(new QuotaService(reservations).usedInWeek(userId, weekStart, weekEnd))
                .isEqualTo(Duration.ofHours(1));
    }
}
