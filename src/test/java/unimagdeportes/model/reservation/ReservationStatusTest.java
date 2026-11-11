package unimagdeportes.model.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ReservationStatusTest {
    @Test
    void lateCancellationConsumesQuotaButReleasesSpace() {
        assertThat(ReservationStatus.CANCELLED_LATE.countsForQuota()).isTrue();
        assertThat(ReservationStatus.CANCELLED_LATE.occupiesSpace()).isFalse();
    }

    @Test
    void noShowStillCountsAndKeepsHistoricalSpaceAllocation() {
        assertThat(ReservationStatus.NO_SHOW.countsForQuota()).isTrue();
        assertThat(ReservationStatus.NO_SHOW.occupiesSpace()).isTrue();
    }
}
