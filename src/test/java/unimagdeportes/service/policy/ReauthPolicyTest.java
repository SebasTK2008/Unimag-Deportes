package unimagdeportes.service.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReauthPolicyTest {
    @Test
    void acceptsOnlyTheTenMinutesFollowingReauthentication() {
        var policy = new ReauthPolicy();
        var now = Instant.parse("2026-11-11T12:00:00Z");
        assertThat(policy.isValid(null, now)).isFalse();
        assertThat(policy.isValid(now.plusSeconds(1), now)).isFalse();
        assertThat(policy.isValid(now.minusSeconds(599), now)).isTrue();
        assertThat(policy.isValid(now.minusSeconds(600), now)).isFalse();
    }
}
