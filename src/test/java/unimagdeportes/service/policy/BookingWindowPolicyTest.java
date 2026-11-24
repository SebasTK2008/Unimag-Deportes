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
        assertThat(policy.isBookable(now, now.plus(Duration.ofDays(7)), tolerance, Duration.ZERO)).isFalse();
        assertThat(policy.isBookable(now, Instant.parse("2026-11-17T04:59:59.999999999Z"),
                tolerance, Duration.ZERO)).isTrue();
        assertThat(policy.isBookable(now, Instant.parse("2026-11-17T05:00:00Z"), tolerance, Duration.ZERO)).isFalse();
    }

    @Test
    void mondayMorningAllowsSundayNightButNotNextMonday() {
        Instant mondayMorning = Instant.parse("2026-11-16T11:50:00Z");
        assertThat(policy.isBookable(mondayMorning, Instant.parse("2026-11-23T04:59:59Z"),
                Duration.ofMinutes(10), Duration.ZERO)).isTrue();
        assertThat(policy.isBookable(mondayMorning, Instant.parse("2026-11-23T05:00:00Z"),
                Duration.ofMinutes(10), Duration.ZERO)).isFalse();
    }

    @Test
    void bookingWindowMovesAtBogotaMidnightRatherThanUtcMidnight() {
        Instant beforeMidnight = Instant.parse("2026-11-12T04:59:59Z");
        Instant nextLastDate = Instant.parse("2026-11-18T13:00:00Z");
        assertThat(policy.isBookable(beforeMidnight, nextLastDate,
                Duration.ofMinutes(10), Duration.ZERO)).isFalse();
        assertThat(policy.isBookable(beforeMidnight.plusSeconds(1), nextLastDate,
                Duration.ofMinutes(10), Duration.ZERO)).isTrue();
    }
}
