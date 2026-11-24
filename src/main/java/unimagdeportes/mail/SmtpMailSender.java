package unimagdeportes.mail;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.MessagingException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.AddressException;
import java.io.UnsupportedEncodingException;

@Component
public class SmtpMailSender implements MailSender {
    private final JavaMailSender sender;
    private final String from;

    public SmtpMailSender(JavaMailSender sender) {
        this(sender, "");
    }

    public SmtpMailSender(JavaMailSender sender, String from) {
        this(sender, from, "");
    }

    @Autowired
    public SmtpMailSender(JavaMailSender sender, @Value("${app.mail.from:}") String from,
            @Value("${app.mail.from-name:}") String fromName) {
        this.sender = sender;
        if (from.contains("\r") || from.contains("\n") || fromName.contains("\r") || fromName.contains("\n")) {
            throw new IllegalArgumentException("El remitente SMTP no debe contener saltos de línea");
        }
        String addressOnly = from.trim();
        String formattedFrom = addressOnly;
        if (!addressOnly.isEmpty()) {
            try {
                var address = new InternetAddress(addressOnly, true);
                address.validate();
                if (!address.getAddress().equals(addressOnly)) {
                    throw new IllegalArgumentException("El remitente SMTP debe ser una dirección de correo");
                }
                if (!fromName.isBlank()) {
                    address.setPersonal(fromName.trim(), "UTF-8");
                    formattedFrom = address.toString();
                }
            } catch (AddressException ex) {
                throw new IllegalArgumentException("Remitente SMTP inválido", ex);
            } catch (UnsupportedEncodingException ex) {
                throw new IllegalArgumentException("No fue posible codificar el nombre del remitente SMTP", ex);
            }
        }
        this.from = formattedFrom;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient);
        if (!from.isEmpty()) message.setFrom(from);
        message.setSubject(subject);
        message.setText(body);
        sendWithRetry(() -> sender.send(message));
    }

    @Override
    public void sendWithPdf(String recipient, String subject, String body, String filename, byte[] pdf) {
        var message = sender.createMimeMessage();
        try {
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(recipient);
            if (!from.isEmpty()) helper.setFrom(from);
            helper.setSubject(subject);
            helper.setText(body);
            helper.addAttachment(filename, new ByteArrayResource(pdf), "application/pdf");
        } catch (MessagingException ex) {
            throw new MailPreparationException("No fue posible preparar el correo con adjunto", ex);
        }
        sendWithRetry(() -> sender.send(message));
    }

    private void sendWithRetry(Runnable send) {
        for (int attempt = 0; ; attempt++) {
            try {
                send.run();
                return;
            } catch (MailSendException ex) {
                if (attempt == 2) throw ex;
                try {
                    Thread.sleep(100L << attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new MailSendException("Envío de correo interrumpido", interrupted);
                }
            }
        }
    }
}
