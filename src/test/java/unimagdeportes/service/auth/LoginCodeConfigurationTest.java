package unimagdeportes.service.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import unimagdeportes.mail.MailSender;
import unimagdeportes.mail.LoginCodeMessage;
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

    @Test
    void rejectsInvalidResendAndHourlyLimits() {
        for (Duration resend : new Duration[]{Duration.ZERO, Duration.ofSeconds(-1)}) {
            assertThatThrownBy(() -> service(resend, 5)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service(Duration.ofMinutes(1), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private LoginCodeService service(Duration resend, int requests) {
        return new LoginCodeService(mock(LoginCodeRepository.class), mock(JdbcTemplate.class), mock(MailSender.class),
                new LoginCodeMessage(""), Clock.systemUTC(), "01234567890123456789012345678901",
                Duration.ofMinutes(15), 5, resend, requests);
    }

    private void assertInvalid(String secret, Duration validity, int attempts) {
        assertThatThrownBy(() -> new LoginCodeService(mock(LoginCodeRepository.class), mock(JdbcTemplate.class),
                mock(MailSender.class), new LoginCodeMessage(""), Clock.systemUTC(), secret, validity, attempts,
                Duration.ofMinutes(1), 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
