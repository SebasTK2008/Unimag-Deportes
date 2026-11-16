package unimagdeportes.service.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BookingWindowPolicyTest {
    private final BookingWindowPolicy policy = new BookingWindowPolicy();

    @Test
    void acceptsBoundariesAndRejectsInstantsOutsideThem() {
        Instant now = Instant.parse("2026-11-10T15:00:00Z");
        Duration tolerance = Duration.ofMinutes(10);
        assertThat(policy.isBookable(now, now.minus(tolerance), tolerance, Duration.ZERO)).isTrue();
        assertThat(policy.isBookable(now, now.minus(tolerance).minusNanos(1), tolerance, Duration.ZERO)).isFalse();
        assertThat(policy.isBookable(now, now.plus(Duration.ofDays(7)), tolerance, Duration.ZERO)).isTrue();
        assertThat(policy.isBookable(now, Instant.parse("2026-11-18T04:59:59Z"), tolerance, Duration.ZERO)).isTrue();
        assertThat(policy.isBookable(now, Instant.parse("2026-11-18T05:00:00Z"), tolerance, Duration.ZERO)).isFalse();
    }
}
