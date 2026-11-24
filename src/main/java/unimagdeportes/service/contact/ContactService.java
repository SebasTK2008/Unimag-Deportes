package unimagdeportes.service.contact;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import unimagdeportes.dto.contact.ContactMessage;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.mail.MailSender;
import unimagdeportes.security.UserPrincipal;

@Service
public class ContactService {
    private final MailSender mail;
    private final String recipient;

    public ContactService(MailSender mail, @Value("${app.mail.contact-recipient:}") String recipient) {
        this.mail = mail;
        this.recipient = recipient.trim();
        if (!this.recipient.isEmpty()) {
            try {
                var address = new InternetAddress(this.recipient, true);
                address.validate();
                if (!address.getAddress().equals(this.recipient)) {
                    throw new IllegalArgumentException("El destinatario de contacto debe ser una dirección de correo");
                }
            } catch (AddressException ex) {
                throw new IllegalArgumentException("Destinatario de contacto inválido", ex);
            }
        }
    }

    public void send(UserPrincipal principal, ContactMessage message) {
        if (recipient.isEmpty()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "CONTACT_NOT_CONFIGURED",
                    "El canal de contacto aún no está disponible.");
        }
        if (message.subject().contains("\r") || message.subject().contains("\n")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "El asunto no es válido.",
                    Map.of("fields", Map.of("subject", "no puede contener saltos de línea")));
        }
        try {
            mail.send(recipient, message.subject(), "De: " + principal.email() + "\n\n" + message.message());
        } catch (MailException ex) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_DELIVERY_FAILED",
                    "No fue posible enviar el mensaje. Inténtalo nuevamente más tarde.", ex);
        }
    }
}
