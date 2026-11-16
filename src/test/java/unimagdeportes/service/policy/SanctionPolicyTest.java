package unimagdeportes.service.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SanctionPolicyTest {
    private final SanctionPolicy policy = new SanctionPolicy();

    @Test
    void absenceMonthUsesTheScheduledEndInBogota() {
        assertThat(policy.absenceMonth(Instant.parse("2026-12-01T04:59:59Z"))).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(policy.absenceMonth(Instant.parse("2026-12-01T05:00:00Z"))).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void monthRangeIsHalfOpenInBogota() {
        var range = policy.range(LocalDate.of(2026, 11, 1));
        assertThat(range.start()).isEqualTo(Instant.parse("2026-11-01T05:00:00Z"));
        assertThat(range.end()).isEqualTo(Instant.parse("2026-12-01T05:00:00Z"));
    }

    @Test
    void thresholdIsReachedAtTheConfiguredCount() {
        assertThat(policy.reachesThreshold(2, 3)).isFalse();
        assertThat(policy.reachesThreshold(3, 3)).isTrue();
        assertThat(policy.reachesThreshold(4, 3)).isTrue();
    }

    @Test
    void suspensionLastsTheConfiguredDaysAndNeverShortensACurrentOne() {
        Instant now = Instant.parse("2026-11-11T12:00:00Z");
        Instant proposed = now.plus(Duration.ofDays(7));
        assertThat(policy.suspensionEnd(now, 7, null)).isEqualTo(proposed);
        assertThat(policy.suspensionEnd(now, 7, now.plus(Duration.ofDays(2)))).isEqualTo(proposed);
        Instant later = now.plus(Duration.ofDays(10));
        assertThat(policy.suspensionEnd(now, 7, later)).isEqualTo(later);
    }
}
