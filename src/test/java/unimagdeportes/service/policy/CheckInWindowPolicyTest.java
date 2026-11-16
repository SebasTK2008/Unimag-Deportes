package unimagdeportes.service.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CheckInWindowPolicyTest {
    private final CheckInWindowPolicy policy = new CheckInWindowPolicy();

    @Test
    void includesOpeningAndExcludesEndUsingReservationSnapshot() {
        Instant start = Instant.parse("2026-11-11T12:00:00Z");
        Instant end = Instant.parse("2026-11-11T13:00:00Z");
        assertThat(policy.isOpen(start.minusSeconds(1201), start, end, 20)).isFalse();
        assertThat(policy.isOpen(start.minusSeconds(1200), start, end, 20)).isTrue();
        assertThat(policy.isOpen(end.minusNanos(1), start, end, 20)).isTrue();
        assertThat(policy.isOpen(end, start, end, 20)).isFalse();
        assertThat(policy.isOpen(end.plusSeconds(1), start, end, 20)).isFalse();
    }
}
