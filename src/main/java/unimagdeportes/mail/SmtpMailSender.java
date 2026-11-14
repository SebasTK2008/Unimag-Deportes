package unimagdeportes.mail;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.MessagingException;
import org.springframework.stereotype.Component;

@Component
public class SmtpMailSender implements MailSender {
    private final JavaMailSender sender;

    public SmtpMailSender(JavaMailSender sender) {
        this.sender = sender;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }

    @Override
    public void sendWithPdf(String recipient, String subject, String body, String filename, byte[] pdf) {
        var message = sender.createMimeMessage();
        try {
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body);
            helper.addAttachment(filename, new ByteArrayResource(pdf), "application/pdf");
        } catch (MessagingException ex) {
            throw new MailPreparationException("No fue posible preparar el correo con adjunto", ex);
        }
        sender.send(message);
    }
}
