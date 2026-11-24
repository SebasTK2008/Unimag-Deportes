package unimagdeportes.integration.contact;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
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
import unimagdeportes.model.user.AppUser;
import unimagdeportes.model.user.Role;
import unimagdeportes.repository.user.AppUserRepository;
import unimagdeportes.security.TokenService;

@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.auth.jwt.secret=test-only-012345678901234567890123456789",
        "app.auth.code.hmac-secret=test-only-hmac-012345678901234567890123456789",
        "APP_MAIL_CONTACT_RECIPIENT=smtp-contact@example.org",
        "app.jobs.reminder-cron=-",
        "app.jobs.reservation-lifecycle-cron=-",
        "spring.docker.compose.enabled=false" })
@Import(TestcontainersConfiguration.class)
@Testcontainers
class ContactSmtpIntegrationTest {
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
        registry.add("spring.mail.properties.mail.smtp.starttls.required", () -> false);
    }

    @LocalServerPort private int port;
    @Autowired private AppUserRepository users;
    @Autowired private TokenService tokens;
    @Autowired private Clock clock;
    @Autowired private JsonMapper json;
    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void closeHttpClient() {
        client.close();
    }

    @Test
    void contactEndpointDeliversExactlyOneMessageToTheEnvironmentConfiguredRecipient() throws Exception {
        var user = new AppUser(UUID.randomUUID(), "contact-smtp@unimagdalena.edu.co", Role.USER, clock.instant());
        user.completeProfile("Persona de prueba");
        users.saveAndFlush(user);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/contact"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + tokens.issue(user))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"subject\":\"Consulta deportiva\",\"message\":\"¿Puedo usar la cancha?\"}"))
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(json.readTree(response.body()).get("message").asString())
                .isEqualTo("Tu mensaje fue enviado al área de deportes.");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        var messages = mailpit("/api/v1/messages");
        assertThat(messages.get("total").asInt()).isEqualTo(1);
        var message = messages.get("messages").get(0);
        assertThat(message.get("To").size()).isEqualTo(1);
        assertThat(message.get("To").get(0).get("Address").asString()).isEqualTo("smtp-contact@example.org");
        assertThat(message.get("Subject").asString()).isEqualTo("Consulta deportiva");
        var delivered = mailpit("/api/v1/message/" + message.get("ID").asString());
        assertThat(delivered.get("Text").asString().replace("\r\n", "\n").stripTrailing())
                .isEqualTo("De: contact-smtp@unimagdalena.edu.co\n\n¿Puedo usar la cancha?");
    }

    private JsonNode mailpit(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://" + MAILPIT.getHost() + ":"
                + MAILPIT.getMappedPort(8025) + path)).timeout(Duration.ofSeconds(10)).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }
}
