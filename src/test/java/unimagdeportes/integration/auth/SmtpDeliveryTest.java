package unimagdeportes.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import unimagdeportes.mail.SmtpMailSender;

@Testcontainers
class SmtpDeliveryTest {
    @Container
    static final GenericContainer<?> MAILPIT = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
            .withExposedPorts(1025, 8025);

    @Test
    void deliversRealSmtpMailInLessThanOneMinute() throws Exception {
        var smtp = new JavaMailSenderImpl();
        smtp.setHost(MAILPIT.getHost());
        smtp.setPort(MAILPIT.getMappedPort(1025));
        smtp.setDefaultEncoding("UTF-8");
        smtp.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "10000");
        smtp.getJavaMailProperties().setProperty("mail.smtp.timeout", "10000");
        Instant start = Instant.now();
        new SmtpMailSender(smtp).send("smtp@unimagdalena.edu.co", "Código de acceso",
                "Tu código de acceso es 123456. Vence en 15 minutos. No lo compartas.");
        var request = HttpRequest.newBuilder(URI.create("http://" + MAILPIT.getHost() + ":"
                + MAILPIT.getMappedPort(8025) + "/api/v1/messages")).timeout(Duration.ofSeconds(10)).GET().build();
        var messages = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(messages.statusCode()).isEqualTo(200);
        assertThat(messages.body()).contains("smtp@unimagdalena.edu.co", "Código de acceso");
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofMinutes(1));
    }

    @Test
    void deliversMailWithPdfAttachmentOverSmtp() throws Exception {
        var smtp = new JavaMailSenderImpl();
        smtp.setHost(MAILPIT.getHost());
        smtp.setPort(MAILPIT.getMappedPort(1025));
        new SmtpMailSender(smtp).sendWithPdf("pdf@unimagdalena.edu.co", "Comprobante PDF",
                "Adjunto", "comprobante.pdf", "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        var request = HttpRequest.newBuilder(URI.create("http://" + MAILPIT.getHost() + ":"
                + MAILPIT.getMappedPort(8025) + "/api/v1/messages")).timeout(Duration.ofSeconds(10)).GET().build();
        var messages = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(messages.statusCode()).isEqualTo(200);
        assertThat(messages.body()).contains("pdf@unimagdalena.edu.co", "Comprobante PDF", "\"Attachments\":1");
    }
}
