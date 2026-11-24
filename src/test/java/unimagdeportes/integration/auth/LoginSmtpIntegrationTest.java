package unimagdeportes.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import unimagdeportes.mail.ReceiptMessage;

@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.auth.jwt.secret=test-only-012345678901234567890123456789",
        "app.auth.code.hmac-secret=test-only-hmac-012345678901234567890123456789",
        "app.auth.admin-emails=",
        "app.cors.allowed-origins=http://localhost:5173",
        "app.mail.contact-url=http://localhost:5173/app/contacto",
        "app.mail.from=deportes@example.org",
        "app.mail.from-name=Unimag Deportes",
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
        registry.add("spring.mail.properties.mail.smtp.starttls.required", () -> false);
    }

    @LocalServerPort private int port;
    @Autowired private JsonMapper json;
    @Autowired private JdbcTemplate jdbc;
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
        assertThat(message.get("From").get("Address").asString()).isEqualTo("deportes@example.org");
        assertThat(message.get("From").get("Name").asString()).isEqualTo("Unimag Deportes");
        assertThat(message.get("To").get(0).get("Address").asString()).isEqualTo(email);
        var loginMail = mailpit("/api/v1/message/" + message.get("ID").asString());
        String text = loginMail.get("Text").asString();
        assertThat(text).contains(ORIGIN + "/app/contacto");
        assertThat(loginMail.get("HTML").asString()).contains("font-size:40px", "text-align:center");
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
        assertReservationReceipt(token, email);
        assertThat(send("POST", "/auth/logout", null, token).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/me", null, null).statusCode()).isEqualTo(401);
    }

    private void assertReservationReceipt(String token, String email) throws Exception {
        UUID space = UUID.randomUUID(), preview = UUID.randomUUID(), panorama = UUID.randomUUID();
        jdbc.update("insert into space_image (id,content_type,storage_key,size_bytes) "
                        + "values (?, 'image/png', ?, 1), (?, 'image/png', ?, 1)",
                preview, "fixtures/" + preview, panorama, "fixtures/" + panorama);
        jdbc.update("insert into space (id,name,capacity,opening_time,closing_time,status,preview_image_id,panorama_image_id)"
                        + " values (?, 'Cancha de prueba SMTP', 10, '07:00', '22:00', 'ACTIVE', ?, ?)",
                space, preview, panorama);
        String start = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(1) + "T14:00:00-05:00";
        assertThat(send("POST", "/reservations", Map.of("spaceId", space, "startAt", start, "slots", 1), token)
                .statusCode()).isEqualTo(201);
        var messages = mailpit("/api/v1/messages").get("messages");
        var confirmation = messages.valueStream()
                .filter(m -> m.get("Subject").asString().startsWith("Reserva confirmada")).findFirst().orElseThrow();
        String id = confirmation.get("ID").asString();
        var delivered = mailpit("/api/v1/message/" + id);
        assertThat(delivered.get("To").get(0).get("Address").asString()).isEqualTo(email);
        assertThat(delivered.get("Text").asString().replace("\r\n", "\n"))
                .contains(ReceiptMessage.CHECK_IN_INFORMATION, "límites diario y semanal", "60 minutos");
        assertThat(delivered.get("HTML").asString()).contains("Reserva confirmada", "Reglas de cancelación");
        var attachment = delivered.get("Attachments").get(0);
        assertThat(attachment.get("FileName").asString()).isEqualTo("comprobante.pdf");
        var request = HttpRequest.newBuilder(URI.create("http://" + MAILPIT.getHost() + ":"
                + MAILPIT.getMappedPort(8025) + "/api/v1/message/" + id + "/part/"
                + attachment.get("PartID").asString())).timeout(Duration.ofSeconds(10)).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        Files.createDirectories(Path.of("target/test-artifacts"));
        Files.write(Path.of("target/test-artifacts/reservation-delivered.pdf"), response.body());
        Files.writeString(Path.of("target/test-artifacts/reservation-confirmation.html"), delivered.get("HTML").asString());
        Files.writeString(Path.of("target/test-artifacts/login-code.html"),
                loginPreview());
        var pdf = new org.openpdf.text.pdf.PdfReader(response.body());
        try {
            assertThat(new org.openpdf.text.pdf.parser.PdfTextExtractor(pdf).getTextFromPage(1))
                    .contains("Persona de conexión local", email, "Cancha de prueba SMTP", "Sin implementos")
                    .doesNotContain(ReceiptMessage.CHECK_IN_INFORMATION);
        } finally {
            pdf.close();
        }
    }

    private String loginPreview() {
        return new unimagdeportes.mail.LoginCodeMessage(ORIGIN + "/app/contacto")
                .compose("001234", Duration.ofMinutes(15)).html();
    }

    private HttpResponse<String> send(String method, String path, Map<String, ?> body, String token)
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
