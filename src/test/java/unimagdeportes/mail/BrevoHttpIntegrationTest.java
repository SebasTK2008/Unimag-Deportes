package unimagdeportes.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.convention.TestBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import unimagdeportes.Application;
import unimagdeportes.TestcontainersConfiguration;

@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.auth.jwt.secret=test-only-012345678901234567890123456789",
        "app.auth.code.hmac-secret=test-only-hmac-012345678901234567890123456789",
        "app.auth.admin-emails=",
        "app.mail.provider=brevo",
        "app.mail.brevo.api-key=test-only-api-key",
        "app.mail.from=deportes@example.org",
        "app.mail.contact-url=https://frontend.example.org/app/contacto",
        "app.mail.contact-recipient=contact@example.org",
        "app.jobs.auth-cleanup-cron=-",
        "app.jobs.reminder-cron=-",
        "app.jobs.reservation-lifecycle-cron=-",
        "app.jobs.storage-cleanup-cron=-",
        "spring.docker.compose.enabled=false" })
@Import(TestcontainersConfiguration.class)
@DirtiesContext
class BrevoHttpIntegrationTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final LinkedBlockingQueue<JsonNode> MESSAGES = new LinkedBlockingQueue<>();
    private static HttpServer server;
    private static volatile int status = 201;

    @TestBean(methodName = "localBrevo")
    private BrevoHttpMailSender brevoHttpMailSender;
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;

    static BrevoHttpMailSender localBrevo() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            try (exchange) {
                MESSAGES.add(JSON.readTree(exchange.getRequestBody().readAllBytes()));
                exchange.sendResponseHeaders(status, -1);
            }
        });
        server.start();
        return new BrevoHttpMailSender(JSON, "test-only-api-key", "deportes@example.org", "Unimag Deportes",
                Duration.ofSeconds(1), Duration.ofSeconds(3),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email"));
    }

    @BeforeEach
    void reset() {
        status = 201;
        MESSAGES.clear();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void loginReceiptContactAndCancellationUseHttp() throws Exception {
        String email = "brevo-flow@unimagdalena.edu.co";
        String token = login(email);
        UUID space = createSpace();
        var reservation = reserve(token, space);
        assertThat(reservation.statusCode()).isEqualTo(201);
        var receipt = MESSAGES.remove();
        assertThat(receipt.get("subject").asString()).startsWith("Reserva confirmada");
        assertThat(receipt.get("to").get(0).get("email").asString()).isEqualTo(email);
        assertThat(receipt.get("htmlContent").asString()).contains("Reglas de cancelación");
        byte[] bytes = Base64.getDecoder().decode(receipt.get("attachment").get(0).get("content").asString());
        var pdf = new org.openpdf.text.pdf.PdfReader(bytes);
        try {
            assertThat(new org.openpdf.text.pdf.parser.PdfTextExtractor(pdf).getTextFromPage(1))
                    .contains(email, "Cancha HTTPS");
        } finally {
            pdf.close();
        }
        assertThat(send("POST", "/contact", Map.of("subject", "Consulta", "message", "¿Hay cancha?"), token)
                .statusCode()).isEqualTo(202);
        var contact = MESSAGES.remove();
        assertThat(contact.get("to").get(0).get("email").asString()).isEqualTo("contact@example.org");
        assertThat(contact.get("textContent").asString()).contains(email, "¿Hay cancha?");
        String id = JSON.readTree(reservation.body()).get("id").asString();
        assertThat(send("POST", "/reservations/" + id + "/cancellation", null, token).statusCode()).isEqualTo(200);
        assertThat(MESSAGES.remove().get("textContent").asString()).containsIgnoringCase("cancel");
    }

    @Test
    void failedDeliveryInvalidatesCodeAndPreservesApiError() throws Exception {
        status = 401;
        String email = "brevo-failure@unimagdalena.edu.co";
        var requested = send("POST", "/auth/code", Map.of("email", email), null);
        assertThat(requested.statusCode()).isEqualTo(503);
        assertThat(JSON.readTree(requested.body()).get("code").asString()).isEqualTo("MAIL_DELIVERY_FAILED");
        assertThat(jdbc.queryForObject("select used_at is not null from login_code where email = ?",
                Boolean.class, email)).isTrue();
        assertThat(MESSAGES).hasSize(1);
    }

    @Test
    void failedReceiptDoesNotRollbackReservationAndContactReturns503() throws Exception {
        String token = login("brevo-receipt-failure@unimagdalena.edu.co");
        status = 503;
        var reservation = reserve(token, createSpace());
        assertThat(reservation.statusCode()).isEqualTo(201);
        UUID id = UUID.fromString(JSON.readTree(reservation.body()).get("id").asString());
        assertThat(jdbc.queryForObject("select count(*) from reservation where id = ?", Integer.class, id)).isEqualTo(1);
        var contact = send("POST", "/contact", Map.of("subject", "Consulta", "message", "Mensaje"), token);
        assertThat(contact.statusCode()).isEqualTo(503);
        assertThat(JSON.readTree(contact.body()).get("code").asString()).isEqualTo("MAIL_DELIVERY_FAILED");
        assertThat(MESSAGES).hasSize(2);
    }

    private String login(String email) throws Exception {
        assertThat(send("POST", "/auth/code", Map.of("email", email), null).statusCode()).isEqualTo(202);
        var mail = MESSAGES.remove();
        var code = Pattern.compile("\\b[0-9]{6}\\b").matcher(mail.get("textContent").asString());
        assertThat(code.find()).isTrue();
        var verified = send("POST", "/auth/verify", Map.of("email", email, "code", code.group()), null);
        assertThat(verified.statusCode()).isEqualTo(200);
        String token = JSON.readTree(verified.body()).get("token").asString();
        assertThat(send("PUT", "/me/profile", Map.of("name", "Persona HTTPS"), token).statusCode()).isEqualTo(200);
        return token;
    }

    private UUID createSpace() {
        UUID id = UUID.randomUUID(), preview = UUID.randomUUID(), panorama = UUID.randomUUID();
        jdbc.update("insert into space_image (id,content_type,storage_key,size_bytes) "
                        + "values (?, 'image/png', ?, 1), (?, 'image/png', ?, 1)",
                preview, "fixtures/" + preview, panorama, "fixtures/" + panorama);
        jdbc.update("insert into space (id,name,capacity,opening_time,closing_time,status,preview_image_id,panorama_image_id)"
                + " values (?, 'Cancha HTTPS', 10, '07:00', '22:00', 'ACTIVE', ?, ?)", id, preview, panorama);
        return id;
    }

    private HttpResponse<String> reserve(String token, UUID space) throws Exception {
        String start = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(1) + "T14:00:00-05:00";
        return send("POST", "/reservations", Map.of("spaceId", space, "startAt", start, "slots", 1), token);
    }

    private HttpResponse<String> send(String method, String path, Map<String, ?> body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
