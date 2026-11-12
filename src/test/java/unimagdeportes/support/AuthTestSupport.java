package unimagdeportes.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import unimagdeportes.TestcontainersConfiguration;
import unimagdeportes.mail.MailSender;

@SpringBootTest(classes = unimagdeportes.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.auth.jwt.secret=test-only-012345678901234567890123456789",
        "app.auth.code.hmac-secret=test-only-hmac-012345678901234567890123456789",
        "app.auth.admin-emails=admin@unimagdalena.edu.co",
        "app.mail.contact-url=https://client.example/contact",
        "app.mail.cancellation-url=https://client.example/reservations/{id}/cancel",
        "app.mail.contact-recipient=deportes@example.org",
        "app.cors.allowed-origins=https://client.example",
        "spring.docker.compose.enabled=false" })
@Import({TestcontainersConfiguration.class, AuthTestSupport.Fixtures.class})
@ActiveProfiles("auth-test")
public abstract class AuthTestSupport {
    public static final Instant START = Instant.parse("2026-11-11T12:00:00Z");
    @LocalServerPort protected int port;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MutableClock clock;
    @Autowired protected TestMail mail;
    @Autowired protected JsonMapper json;
    @Autowired protected JwtDecoder decoder;
    @Autowired protected JwtEncoder encoder;
    protected final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void resetState() {
        jdbc.execute("truncate table audit_log, login_code, login_code_request_ip, app_user cascade");
        resetUsageParameters();
        clock.set(START);
        mail.bodies.clear();
        mail.sentCount.set(0);
        mail.failing = false;
    }

    protected void resetUsageParameters() {
        jdbc.execute("truncate table usage_parameters cascade");
        jdbc.update("insert into usage_parameters "
                + "(id, version_number, weekly_quota_hours, no_show_threshold, created_at, created_by) "
                + "values ('00000000-0000-0000-0000-000000000001', 1, 4, 3, ?, null)",
                java.sql.Timestamp.from(START));
        jdbc.update("insert into usage_parameters_head (id, current_version_id) "
                + "values (1, '00000000-0000-0000-0000-000000000001')");
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Fixtures {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
        @Bean @Primary TestMail testMail() { return new TestMail(); }
    }

    // Exercise shared security without implementing endpoints belonging to other UCs.
    @RestController
    @Profile("auth-test")
    public static class ProtectedProbe {
        @RequestMapping({"/test/protected", "/admin/blocks"})
        ResponseEntity<Void> protectedAction() { return ResponseEntity.noContent().build(); }
    }

    public static class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(START);
        public void set(Instant instant) { now.set(instant); }
        public void advance(Duration duration) { now.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneId.of("America/Bogota"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return now.get(); }
    }

    public static class TestMail implements MailSender {
        public final Map<String, String> bodies = new ConcurrentHashMap<>();
        public volatile boolean failing;
        public final AtomicInteger sentCount = new AtomicInteger();
        @Override public void send(String recipient, String subject, String body) {
            if (failing) throw new MailSendException("smtp-test-secret");
            sentCount.incrementAndGet();
            bodies.put(recipient, body);
        }
        @Override public void sendWithPdf(String recipient, String subject, String body, String filename, byte[] pdf) {
            send(recipient, subject, body);
        }
    }

    protected HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        return response;
    }

    protected HttpResponse<String> requestCode(String email) throws Exception {
        return send("POST", "/auth/code", json.writeValueAsString(Map.of("email", email)), null);
    }

    protected String code(String email) {
        var matcher = Pattern.compile("\\b[0-9]{6}\\b").matcher(mail.bodies.get(email));
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }

    protected HttpResponse<String> verify(String email, String code) throws Exception {
        return send("POST", "/auth/verify", json.writeValueAsString(Map.of("email", email, "code", code)), null);
    }

    protected String login(String email) throws Exception {
        assertThat(requestCode(email).statusCode()).isEqualTo(202);
        var response = verify(email, code(email));
        assertThat(response.statusCode()).isEqualTo(200);
        return body(response).get("token").asString();
    }

    protected String completeLogin(String email) throws Exception {
        String token = login(email);
        assertThat(send("PUT", "/me/profile", "{\"name\":\"Persona de prueba\"}", token).statusCode()).isEqualTo(200);
        return token;
    }

    protected JsonNode body(HttpResponse<String> response) { return json.readTree(response.body()); }

    protected void error(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(body(response).get("code").asString()).isEqualTo(code);
    }
}
