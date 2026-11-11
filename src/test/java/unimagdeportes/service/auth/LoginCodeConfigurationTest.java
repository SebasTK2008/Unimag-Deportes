package unimagdeportes.service.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import unimagdeportes.mail.MailSender;
import unimagdeportes.repository.auth.LoginCodeRepository;

class LoginCodeConfigurationTest {
    @Test
    void rejectsMissingOrShortHmacAndInvalidDeploymentLimitsAtStartup() {
        assertInvalid("", Duration.ofMinutes(15), 5);
        assertInvalid("too-short", Duration.ofMinutes(15), 5);
        assertInvalid("01234567890123456789012345678901", Duration.ZERO, 5);
        assertInvalid("01234567890123456789012345678901", Duration.ofSeconds(-1), 5);
        assertInvalid("01234567890123456789012345678901", Duration.ofMinutes(15), 0);
    }

    private void assertInvalid(String secret, Duration validity, int attempts) {
        assertThatThrownBy(() -> new LoginCodeService(mock(LoginCodeRepository.class), mock(JdbcTemplate.class),
                mock(MailSender.class), Clock.systemUTC(), secret, validity, attempts))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
