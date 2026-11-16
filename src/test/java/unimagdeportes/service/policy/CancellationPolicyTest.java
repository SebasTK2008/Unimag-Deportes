package unimagdeportes.service.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CancellationPolicyTest {
    private final CancellationPolicy policy = new CancellationPolicy();

    @Test
    void onTimeExactlyAtTheThreshold() {
        Instant start = Instant.parse("2026-11-11T14:00:00Z");
        Instant now = start.minus(Duration.ofMinutes(60));
        assertThat(policy.isLate(now, start, 60)).isFalse();
        assertThat(policy.hoursCounted(start, start.plus(Duration.ofHours(2)), false)).isZero();
    }

    @Test
    void lateWhenLessThanTheThresholdRemains() {
        Instant start = Instant.parse("2026-11-11T14:00:00Z");
        assertThat(policy.isLate(start.minus(Duration.ofMinutes(59)), start, 60)).isTrue();
    }

    @Test
    void lateWhenTheSlotAlreadyStarted() {
        Instant start = Instant.parse("2026-11-11T14:00:00Z");
        assertThat(policy.isLate(start.plusSeconds(1), start, 60)).isTrue();
    }

    @Test
    void slotEndsExactlyAtItsEndInstant() {
        Instant end = Instant.parse("2026-11-11T15:00:00Z");
        assertThat(policy.hasEnded(end.minusSeconds(1), end)).isFalse();
        assertThat(policy.hasEnded(end, end)).isTrue();
        assertThat(policy.hasEnded(end.plusSeconds(1), end)).isTrue();
    }

    @Test
    void lateCancellationCountsTheFullReservationDuration() {
        Instant start = Instant.parse("2026-11-11T14:00:00Z");
        Instant end = start.plus(Duration.ofHours(2));
        assertThat(policy.hoursCounted(start, end, true)).isEqualTo(2);
    }
}
