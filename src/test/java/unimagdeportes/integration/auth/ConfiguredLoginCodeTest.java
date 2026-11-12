package unimagdeportes.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.mail.LoginCodeMessage;
import unimagdeportes.repository.auth.LoginCodeRepository;
import unimagdeportes.service.auth.LoginCodeService;
import unimagdeportes.support.AuthTestSupport;

class ConfiguredLoginCodeTest extends AuthTestSupport {
    @Autowired LoginCodeRepository codes;
    @Autowired TransactionTemplate transactions;

    @Test
    void missingContactDoesNotSendCodeAndReturnsDeliveryFailure() {
        var service = new LoginCodeService(codes, jdbc, mail, new LoginCodeMessage(""), clock,
                "test-config-012345678901234567890123456789", Duration.ofMinutes(15), 5);
        String email = "missing-contact@unimagdalena.edu.co";
        BusinessException error = transactions.execute(status -> {
            try {
                service.request(email, "127.0.0.1");
                throw new AssertionError("Se esperaba un rechazo");
            } catch (BusinessException ex) {
                return ex;
            }
        });
        assertThat(error.getCode()).isEqualTo("MAIL_DELIVERY_FAILED");
        assertThat(mail.bodies).doesNotContainKey(email);
        assertThat(codes.findFirstByEmailOrderByCreatedAtDesc(email).orElseThrow().getUsedAt()).isEqualTo(START);
    }

    @Test
    void deploymentCanChangeValidityAndAttemptLimit() {
        var service = new LoginCodeService(codes, jdbc, mail, new LoginCodeMessage("https://client.example/contact"), clock,
                "test-config-012345678901234567890123456789", Duration.ofMinutes(2), 2);
        String email = "configured@unimagdalena.edu.co";
        transactions.executeWithoutResult(status -> service.request(email, "127.0.0.1"));
        var stored = codes.findFirstByEmailOrderByCreatedAtDesc(email).orElseThrow();
        assertThat(stored.getExpiresAt()).isEqualTo(START.plusSeconds(120));
        assertThat(mail.bodies.get(email)).contains("2 minutos");
        assertThat(mail.bodies.get(email)).contains("https://client.example/contact");
        String wrong = code(email).equals("000000") ? "000001" : "000000";
        assertFailureCommitted(service, email, wrong, "INVALID_CODE");
        assertFailureCommitted(service, email, wrong, "TOO_MANY_ATTEMPTS");
        assertThat(codes.findById(stored.getId()).orElseThrow().getAttempts()).isEqualTo(2);
        assertThat(codes.findById(stored.getId()).orElseThrow().getUsedAt()).isEqualTo(START);
        clock.advance(Duration.ofSeconds(60));
        transactions.executeWithoutResult(status -> service.request(email, "127.0.0.1"));
        clock.advance(Duration.ofMinutes(2));
        assertFailureCommitted(service, email, code(email), "CODE_EXPIRED");
    }

    private void assertFailureCommitted(LoginCodeService service, String email, String code, String expected) {
        // Keep the same commit-on-business-error behavior as the Spring service proxy.
        BusinessException error = transactions.execute(status -> {
            try {
                service.verify(email, code);
                throw new AssertionError("Se esperaba un rechazo");
            } catch (BusinessException ex) {
                return ex;
            }
        });
        assertThat(error.getCode()).isEqualTo(expected);
    }
}
