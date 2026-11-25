package unimagdeportes.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class BrevoHttpMailSenderTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final LinkedBlockingQueue<Captured> requests = new LinkedBlockingQueue<>();
    private final CountDownLatch releaseResponse = new CountDownLatch(1);
    private HttpServer server;
    private BrevoHttpMailSender sender;
    private volatile int status = 201;
    private volatile boolean blockResponse;

    record Captured(String method, String path, String key, String contentType, JsonNode body) {}

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                requests.add(new Captured(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("api-key"),
                        exchange.getRequestHeaders().getFirst("Content-Type"),
                        json.readTree(exchange.getRequestBody().readAllBytes())));
                if (blockResponse) {
                    try {
                        releaseResponse.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
                exchange.sendResponseHeaders(status, -1);
            }
        });
        server.start();
        sender = createSender(Duration.ofSeconds(2));
    }

    private BrevoHttpMailSender createSender(Duration timeout) {
        return new BrevoHttpMailSender(json, "test-api-key", "deportes@example.org", "Área de Deportes",
                Duration.ofSeconds(1), timeout,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email"));
    }

    @AfterEach
    void stopServer() {
        releaseResponse.countDown();
        sender.close();
        server.stop(0);
    }

    @Test
    void sendsHtmlAndTextWithApiKeyAndVerifiedSender() throws Exception {
        sender.send("persona@example.org", "Código de acceso — ñ", new MailBody("Código 001234", "<p>001234</p>"));
        var captured = requests.poll(1, TimeUnit.SECONDS);
        assertThat(captured).isNotNull();
        assertThat(captured.method()).isEqualTo("POST");
        assertThat(captured.path()).isEqualTo("/v3/smtp/email");
        assertThat(captured.key()).isEqualTo("test-api-key");
        assertThat(captured.contentType()).isEqualTo("application/json");
        var body = captured.body();
        assertThat(body.get("sender").get("email").asString()).isEqualTo("deportes@example.org");
        assertThat(body.get("sender").get("name").asString()).isEqualTo("Área de Deportes");
        assertThat(body.get("to").get(0).get("email").asString()).isEqualTo("persona@example.org");
        assertThat(body.get("subject").asString()).isEqualTo("Código de acceso — ñ");
        assertThat(body.get("textContent").asString()).isEqualTo("Código 001234");
        assertThat(body.get("htmlContent").asString()).isEqualTo("<p>001234</p>");
        assertThat(body.has("attachment")).isFalse();
    }

    @Test
    void escapesPlainTextAndKeepsTheOriginalText() {
        sender.send("persona@example.org", "Consulta", "¿Cancha? <script>&\"\nSegunda línea");
        var body = requests.remove().body();
        assertThat(body.get("textContent").asString()).isEqualTo("¿Cancha? <script>&\"\nSegunda línea");
        assertThat(body.get("htmlContent").asString()).contains("&lt;script&gt;", "&amp;", "&quot;", "\n")
                .doesNotContain("<script>");
    }

    @Test
    void sendsUnchangedPdfBytesAlongsideBothBodies() {
        byte[] pdf = "%PDF-1.7\ncomprobante ñ".getBytes(StandardCharsets.UTF_8);
        sender.sendWithPdf("persona@example.org", "Reserva", new MailBody("Texto", "<p>HTML</p>"),
                "comprobante.pdf", pdf);
        var body = requests.remove().body();
        var attachment = body.get("attachment").get(0);
        assertThat(attachment.get("name").asString()).isEqualTo("comprobante.pdf");
        assertThat(Base64.getDecoder().decode(attachment.get("content").asString())).isEqualTo(pdf);
        assertThat(body.get("htmlContent").asString()).isEqualTo("<p>HTML</p>");
    }

    @Test
    void supportsPlainTextPdfOverloadAndRejectsEmptyAttachments() {
        sender.sendWithPdf("persona@example.org", "Reserva", "Texto <seguro>", "comprobante.pdf", new byte[]{1, 2});
        assertThat(requests.remove().body().get("htmlContent").asString()).contains("&lt;seguro&gt;");
        assertThatThrownBy(() -> sender.sendWithPdf("persona@example.org", "Reserva", "Texto", "", new byte[0]))
                .isInstanceOf(MailPreparationException.class);
        assertThat(requests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 202, 301, 400, 401, 403, 429, 500, 503})
    void translatesRejectionsWithoutRetryOrExposingCredentials(int responseStatus, CapturedOutput output) {
        status = responseStatus;
        assertThatThrownBy(() -> sender.send("persona@example.org", "Asunto", "Código secreto"))
                .isInstanceOf(MailSendException.class)
                .hasMessageContaining("HTTP " + responseStatus)
                .hasMessageNotContaining("test-api-key")
                .hasMessageNotContaining("Código secreto");
        assertThat(requests).hasSize(1);
        assertThat(output.getAll()).contains("MAIL_BREVO_REJECTED: http_status=" + responseStatus)
                .doesNotContain("test-api-key", "Código secreto", "persona@example.org", "deportes@example.org",
                        "Asunto");
    }

    @Test
    void timesOutWithoutResending(CapturedOutput output) {
        sender.close();
        sender = createSender(Duration.ofSeconds(1));
        sender.send("persona@example.org", "Preparación", "Texto");
        requests.clear();
        blockResponse = true;
        assertThatThrownBy(() -> sender.send("persona@example.org", "Asunto", "Texto"))
                .isInstanceOf(MailSendException.class)
                .hasCauseInstanceOf(java.net.http.HttpTimeoutException.class);
        assertThat(requests).hasSize(1);
        assertThat(output.getAll()).contains("MAIL_BREVO_IO_ERROR", "exception_type=HttpTimeoutException")
                .doesNotContain("test-api-key", "persona@example.org", "deportes@example.org");
    }

    @Test
    void translatesConnectionFailure(CapturedOutput output) {
        server.stop(0);
        assertThatThrownBy(() -> sender.send("persona@example.org", "Asunto", "Texto"))
                .isInstanceOf(MailSendException.class);
        assertThat(requests).isEmpty();
        assertThat(output.getAll()).contains("MAIL_BREVO_IO_ERROR", "exception_type=ConnectException")
                .doesNotContain("test-api-key", "persona@example.org", "deportes@example.org");
    }

    @Test
    void preservesThreadInterruption(CapturedOutput output) {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> sender.send("persona@example.org", "Asunto", "Texto"))
                    .isInstanceOf(MailSendException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        assertThat(output.getAll()).contains("MAIL_BREVO_INTERRUPTED")
                .doesNotContain("test-api-key", "persona@example.org", "deportes@example.org");
    }
}
