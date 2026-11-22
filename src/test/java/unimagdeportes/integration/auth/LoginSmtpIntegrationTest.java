package unimagdeportes.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import unimagdeportes.Application;
import unimagdeportes.TestcontainersConfiguration;

@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.auth.jwt.secret=test-only-012345678901234567890123456789",
        "app.auth.code.hmac-secret=test-only-hmac-012345678901234567890123456789",
        "app.auth.admin-emails=",
        "app.cors.allowed-origins=http://localhost:5173",
        "app.mail.contact-url=http://localhost:5173/app/contacto",
        "app.jobs.auth-cleanup-cron=-",
        "app.jobs.reminder-cron=-",
        "app.jobs.reservation-lifecycle-cron=-",
        "spring.docker.compose.enabled=false" })
@Import(TestcontainersConfiguration.class)
@Testcontainers
class LoginSmtpIntegrationTest {
    private static final String ORIGIN = "http://localhost:5173";

    @Container
    @SuppressWarnings("resource") // The @Testcontainers extension stops this shared container after the class.
    static final GenericContainer<?> MAILPIT = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27"))
            .withExposedPorts(1025, 8025);

    @DynamicPropertySource
    static void smtpProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
        registry.add("spring.mail.username", () -> "");
        registry.add("spring.mail.password", () -> "");
        registry.add("spring.mail.properties.mail.smtp.auth", () -> false);
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> false);
    }

    @LocalServerPort private int port;
    @Autowired private JsonMapper json;
    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void closeHttpClient() {
        client.close();
    }

    @Test
    void localClientCanCompleteRealEmailLoginAndReadProtectedResources() throws Exception {
        String email = "local-connection@unimagdalena.edu.co";
        var requested = send("POST", "/auth/code", Map.of("email", email), null);
        assertThat(requested.statusCode()).isEqualTo(202);

        var messages = mailpit("/api/v1/messages");
        assertThat(messages.get("total").asInt()).isEqualTo(1);
        var message = messages.get("messages").get(0);
        assertThat(message.get("To").get(0).get("Address").asString()).isEqualTo(email);
        String text = mailpit("/api/v1/message/" + message.get("ID").asString()).get("Text").asString();
        assertThat(text).contains(ORIGIN + "/app/contacto");
        var code = Pattern.compile("\\b[0-9]{6}\\b").matcher(text);
        assertThat(code.find()).isTrue();

        var verified = send("POST", "/auth/verify", Map.of("email", email, "code", code.group()), null);
        assertThat(verified.statusCode()).isEqualTo(200);
        var session = json.readTree(verified.body());
        assertThat(session.get("needsName").asBoolean()).isTrue();
        String token = session.get("token").asString();
        assertThat(token).isNotBlank();

        var incomplete = send("GET", "/spaces", null, token);
        assertThat(incomplete.statusCode()).isEqualTo(403);
        assertThat(json.readTree(incomplete.body()).get("code").asString()).isEqualTo("PROFILE_INCOMPLETE");
        assertThat(send("PUT", "/me/profile", Map.of("name", "Persona de conexión local"), token).statusCode())
                .isEqualTo(200);

        var profile = send("GET", "/me", null, token);
        assertThat(profile.statusCode()).isEqualTo(200);
        assertThat(json.readTree(profile.body()).get("role").asString()).isEqualTo("USER");
        assertThat(json.readTree(profile.body()).get("needsName").asBoolean()).isFalse();
        for (String path : new String[]{"/spaces", "/rules", "/quota/me", "/reservations/mine"}) {
            assertThat(send("GET", path, null, token).statusCode()).as(path).isEqualTo(200);
        }
        assertThat(send("POST", "/auth/logout", null, token).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/me", null, null).statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> send(String method, String path, Map<String, String> body, String token)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Origin", ORIGIN);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(ORIGIN);
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        return response;
    }

    private JsonNode mailpit(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://" + MAILPIT.getHost() + ":"
                + MAILPIT.getMappedPort(8025) + path)).timeout(Duration.ofSeconds(10)).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }
}
