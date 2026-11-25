package unimagdeportes.mail;

import jakarta.annotation.PreDestroy;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "brevo")
public class BrevoHttpMailSender implements MailSender, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(BrevoHttpMailSender.class);
    private static final URI ENDPOINT = URI.create("https://api.brevo.com/v3/smtp/email");
    private final HttpClient client;
    private final JsonMapper json;
    private final URI endpoint;
    private final String apiKey;
    private final Map<String, String> from;
    private final Duration requestTimeout;

    @Autowired
    public BrevoHttpMailSender(JsonMapper json,
            @Value("${app.mail.brevo.api-key:}") String apiKey,
            @Value("${app.mail.from:}") String from,
            @Value("${app.mail.from-name:}") String fromName,
            @Value("${app.mail.brevo.connect-timeout:PT5S}") Duration connectTimeout,
            @Value("${app.mail.brevo.request-timeout:PT10S}") Duration requestTimeout) {
        this(json, apiKey, from, fromName, connectTimeout, requestTimeout, ENDPOINT);
    }

    // Endpoint injection is package-private for isolated HTTP tests, never configurable in production.
    BrevoHttpMailSender(JsonMapper json, String apiKey, String from, String fromName,
            Duration connectTimeout, Duration requestTimeout, URI endpoint) {
        requireTimeout(connectTimeout);
        requireTimeout(requestTimeout);
        if (apiKey.isBlank() || apiKey.contains("\r") || apiKey.contains("\n")) {
            throw new IllegalArgumentException("La API key de Brevo es obligatoria y no admite saltos de línea");
        }
        if (from.isBlank() || from.contains("\r") || from.contains("\n")
                || fromName.contains("\r") || fromName.contains("\n")) {
            throw new IllegalArgumentException("Remitente Brevo obligatorio o inválido");
        }
        String addressOnly = from.trim();
        try {
            var address = new InternetAddress(addressOnly, true);
            address.validate();
            if (!address.getAddress().equals(addressOnly)) {
                throw new IllegalArgumentException("El remitente Brevo debe ser una dirección simple");
            }
        } catch (AddressException ex) {
            throw new IllegalArgumentException("Remitente Brevo inválido", ex);
        }
        this.from = fromName.isBlank() ? Map.of("email", addressOnly)
                : Map.of("email", addressOnly, "name", fromName.trim());
        this.json = json;
        this.apiKey = apiKey.trim();
        this.requestTimeout = requestTimeout;
        this.endpoint = endpoint;
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private static void requireTimeout(Duration timeout) {
        if (timeout.toMillis() < 1 || timeout.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException("Timeout de Brevo debe estar entre 1 ms y 1 minuto");
        }
    }

    @Override
    public void send(String recipient, String subject, String body) {
        send(recipient, subject, textBody(body));
    }

    @Override
    public void send(String recipient, String subject, MailBody body) {
        sendMessage(recipient, subject, body, null, null);
    }

    @Override
    public void sendWithPdf(String recipient, String subject, String body, String filename, byte[] pdf) {
        sendWithPdf(recipient, subject, textBody(body), filename, pdf);
    }

    @Override
    public void sendWithPdf(String recipient, String subject, MailBody body, String filename, byte[] pdf) {
        if (filename == null || filename.isBlank() || pdf == null || pdf.length == 0) {
            throw new MailPreparationException("El comprobante PDF y su nombre son obligatorios");
        }
        sendMessage(recipient, subject, body, filename, pdf);
    }

    private static MailBody textBody(String text) {
        return new MailBody(text, "<pre>" + HtmlUtils.htmlEscape(text, "UTF-8") + "</pre>");
    }

    private void sendMessage(String recipient, String subject, MailBody body, String filename, byte[] pdf) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("sender", from);
        payload.put("to", List.of(Map.of("email", recipient)));
        payload.put("subject", subject);
        payload.put("textContent", body.text());
        payload.put("htmlContent", body.html());
        if (filename != null) {
            payload.put("attachment", List.of(Map.of("name", filename,
                    "content", Base64.getEncoder().encodeToString(pdf))));
        }
        final String encoded;
        try {
            encoded = json.writeValueAsString(payload);
        } catch (tools.jackson.core.JacksonException ex) {
            LOG.warn("MAIL_BREVO_PREPARATION_FAILED: no fue posible serializar el correo");
            throw new MailPreparationException("No fue posible preparar el correo de Brevo", ex);
        }
        var request = HttpRequest.newBuilder(endpoint).timeout(requestTimeout)
                .header("api-key", apiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(encoded)).build();
        try {
            // No retries: an ambiguous failure may occur after the provider accepted the message.
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 201) {
                LOG.warn("MAIL_BREVO_REJECTED: http_status={}", response.statusCode());
                throw new MailSendException("Brevo rechazó el envío (HTTP " + response.statusCode() + ")");
            }
        } catch (IOException ex) {
            // Exception messages and provider bodies can contain sensitive data; log only types.
            LOG.warn("MAIL_BREVO_IO_ERROR: exception_type={}, cause_type={}",
                    ex.getClass().getSimpleName(),
                    ex.getCause() == null ? "none" : ex.getCause().getClass().getSimpleName());
            throw new MailSendException("No fue posible completar el envío HTTPS a Brevo", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.warn("MAIL_BREVO_INTERRUPTED: envío HTTPS interrumpido");
            throw new MailSendException("Envío HTTPS a Brevo interrumpido", ex);
        }
    }

    @Override
    @PreDestroy
    public void close() {
        client.close();
    }
}
