package unimagdeportes.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import unimagdeportes.support.AuthTestSupport;

class LoginCodeIntegrationTest extends AuthTestSupport {
    @Test
    void normalizesEmailAndCreatesOnlyOneAccount() throws Exception {
        assertThat(requestCode("PERSON@UNIMAGDALENA.EDU.CO").statusCode()).isEqualTo(202);
        var verified = verify("person@unimagdalena.edu.co", code("person@unimagdalena.edu.co"));
        assertThat(verified.statusCode()).isEqualTo(200);
        clock.advance(Duration.ofSeconds(60));
        login("person@unimagdalena.edu.co");
        assertThat(jdbc.queryForObject("select count(*) from app_user", Integer.class)).isEqualTo(1);
    }

    @Test
    void fifthFailureInvalidatesAndFailedAttemptsSurviveTheErrorResponse() throws Exception {
        String email = "attempts@unimagdalena.edu.co";
        requestCode(email);
        String wrong = code(email).equals("000000") ? "000001" : "000000";
        for (int attempt = 1; attempt < 5; attempt++) {
            var response = verify(email, wrong);
            error(response, 400, "INVALID_CODE");
            assertThat(body(response).get("details").get("attemptsRemaining").asInt()).isEqualTo(5 - attempt);
        }
        error(verify(email, wrong), 400, "TOO_MANY_ATTEMPTS");
        error(verify(email, code(email)), 400, "CODE_INVALIDATED");
        assertThat(jdbc.queryForObject("select attempts from login_code where email = ?", Integer.class, email)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from app_user", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'LOGIN_FAILED'", Integer.class)).isEqualTo(6);
    }

    @Test
    void expiresAtTheExactBoundaryAndIsSingleUse() throws Exception {
        String email = "expires@unimagdalena.edu.co";
        requestCode(email);
        clock.advance(Duration.ofMinutes(15));
        error(verify(email, code(email)), 400, "CODE_EXPIRED");
        requestCode(email);
        String code = code(email);
        assertThat(verify(email, code).statusCode()).isEqualTo(200);
        error(verify(email, code), 400, "CODE_INVALIDATED");
    }

    @Test
    void resendInvalidatesThePreviousRecordAndKeepsItsRequestHistory() throws Exception {
        String email = "resend@unimagdalena.edu.co";
        requestCode(email);
        error(requestCode(email), 429, "RESEND_TOO_SOON");
        clock.advance(Duration.ofSeconds(59));
        var limited = requestCode(email);
        error(limited, 429, "RESEND_TOO_SOON");
        assertThat(body(limited).get("details").get("retryAfterSeconds").asLong()).isEqualTo(1);
        clock.advance(Duration.ofSeconds(1));
        assertThat(requestCode(email).statusCode()).isEqualTo(202);
        assertThat(jdbc.queryForObject("select count(*) from login_code where used_at is not null", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from login_code", Integer.class)).isEqualTo(2);
    }

    @Test
    void enforcesTheRollingHourlyEmailLimitWithoutCountingRejectedRequests() throws Exception {
        String email = "hour@unimagdalena.edu.co";
        for (int i = 0; i < 5; i++) {
            assertThat(requestCode(email).statusCode()).isEqualTo(202);
            clock.advance(Duration.ofSeconds(60));
        }
        var limited = requestCode(email);
        error(limited, 429, "CODE_REQUEST_LIMIT");
        assertThat(body(limited).get("details").get("retryAfterSeconds").asLong()).isEqualTo(3300);
        assertThat(jdbc.queryForObject("select count(*) from login_code", Integer.class)).isEqualTo(5);
        clock.set(START.plus(Duration.ofHours(1)));
        assertThat(requestCode(email).statusCode()).isEqualTo(202);
    }

    @Test
    void enforcesTheIpLimitAcrossDifferentEmails() throws Exception {
        for (int i = 0; i < 30; i++) {
            assertThat(requestCode("ip" + i + "@unimagdalena.edu.co").statusCode()).isEqualTo(202);
        }
        var response = requestCode("other@unimagdalena.edu.co");
        error(response, 429, "IP_CODE_REQUEST_LIMIT");
        var forwarded = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                "http://localhost:" + port + "/auth/code"))
                .header("Content-Type", "application/json").header("X-Forwarded-For", "203.0.113.20")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"email\":\"spoof@unimagdalena.edu.co\"}"))
                .build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        error(forwarded, 429, "IP_CODE_REQUEST_LIMIT");
        assertThat(body(response).get("details").get("retryAfterSeconds").asInt()).isEqualTo(3600);
        assertThat(jdbc.queryForObject("select count(*) from login_code_request_ip", Integer.class)).isEqualTo(30);
        clock.advance(Duration.ofHours(1));
        assertThat(requestCode("other@unimagdalena.edu.co").statusCode()).isEqualTo(202);
    }

    @Test
    void failedDeliveryStillConsumesRequestLimitsAndDoesNotExposeSmtpDetails() throws Exception {
        String email = "mail@unimagdalena.edu.co";
        mail.failing = true;
        var response = requestCode(email);
        error(response, 503, "MAIL_DELIVERY_FAILED");
        assertThat(response.body()).doesNotContain("smtp-test-secret");
        assertThat(jdbc.queryForObject("select count(*) from login_code where used_at is not null", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from login_code_request_ip", Integer.class)).isEqualTo(1);
        error(requestCode(email), 429, "RESEND_TOO_SOON");
        mail.failing = false;
        clock.advance(Duration.ofSeconds(60));
        assertThat(requestCode(email).statusCode()).isEqualTo(202);
        assertThat(verify(email, code(email)).statusCode()).isEqualTo(200);
    }

    @Test
    void concurrentVerificationsCanOnlyConsumeTheCodeOnce() throws Exception {
        String email = "concurrent@unimagdalena.edu.co";
        requestCode(email);
        String code = code(email);
        var barrier = new CyclicBarrier(2);
        Callable<Integer> verification = () -> {
            barrier.await();
            return verify(email, code).statusCode();
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = executor.invokeAll(List.of(verification, verification));
            assertThat(List.of(futures.getFirst().get(), futures.getLast().get())).containsExactlyInAnyOrder(200, 400);
        }
        assertThat(jdbc.queryForObject("select count(*) from app_user", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'LOGIN'", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentRequestsCannotBypassTheResendLimit() throws Exception {
        var barrier = new CyclicBarrier(2);
        Callable<Integer> request = () -> {
            barrier.await();
            return requestCode("race@unimagdalena.edu.co").statusCode();
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = executor.invokeAll(List.of(request, request));
            assertThat(List.of(futures.getFirst().get(), futures.getLast().get())).containsExactlyInAnyOrder(202, 429);
        }
        assertThat(jdbc.queryForObject("select count(*) from login_code", Integer.class)).isEqualTo(1);
    }
}
