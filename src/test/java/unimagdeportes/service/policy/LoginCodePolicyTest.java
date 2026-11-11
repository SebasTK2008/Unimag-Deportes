package unimagdeportes.service.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class LoginCodePolicyTest {
    private final byte[] secret = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);

    @Test
    void generatesSixDigitsIncludingLeadingZeroes() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(1_000_000)).thenReturn(42);
        assertThat(new LoginCodePolicy(random, secret).generate()).isEqualTo("000042");
    }

    @Test
    void hashesAreBoundToEmailCodeAndKey() {
        var policy = new LoginCodePolicy(new SecureRandom(), secret);
        String hash = policy.hash("a@unimagdalena.edu.co", "000042");
        assertThat(hash).hasSize(64).doesNotContain("000042");
        assertThat(policy.matches("a@unimagdalena.edu.co", "000042", hash)).isTrue();
        assertThat(policy.matches("b@unimagdalena.edu.co", "000042", hash)).isFalse();
        assertThat(policy.matches("a@unimagdalena.edu.co", "000043", hash)).isFalse();
        var other = new LoginCodePolicy(new SecureRandom(), "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8));
        assertThat(other.matches("a@unimagdalena.edu.co", "000042", hash)).isFalse();
    }

    @Test
    void rejectsShortKeys() {
        assertThatThrownBy(() -> new LoginCodePolicy(new SecureRandom(), new byte[31]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
