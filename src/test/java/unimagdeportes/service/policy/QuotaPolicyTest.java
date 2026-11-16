package unimagdeportes.service.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuotaPolicyTest {
    private final QuotaPolicy policy = new QuotaPolicy();

    @Test
    void countsOnlyThePartWithinTheWeek() {
        Instant monday = Instant.parse("2026-11-09T05:00:00Z");
        var crossing = new QuotaPolicy.Interval(monday.minus(Duration.ofHours(1)),
                monday.plus(Duration.ofHours(1)));
        var outside = new QuotaPolicy.Interval(monday.minus(Duration.ofHours(2)), monday);

        assertThat(policy.usedInWeek(List.of(crossing, outside), monday, monday.plus(Duration.ofDays(7))))
                .isEqualTo(Duration.ofHours(1));
    }

    @Test
    void availableQuotaNeverBecomesNegative() {
        assertThat(policy.available(Duration.ofHours(4), Duration.ofHours(3))).isEqualTo(Duration.ofHours(1));
        assertThat(policy.available(Duration.ofHours(4), Duration.ofHours(5))).isEqualTo(Duration.ZERO);
    }
}
