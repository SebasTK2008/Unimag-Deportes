package unimagdeportes.mail;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.stereotype.Component;

@Component
public class LoginCodeMessage {
    private final String contactUrl;

    public LoginCodeMessage(@Value("${app.mail.contact-url:}") String contactUrl) {
        this.contactUrl = contactUrl.trim();
    }

    public String render(String code, Duration validity) {
        if (contactUrl.isEmpty()) {
            throw new MailPreparationException("El canal de contacto no está configurado");
        }
        String body = "Tu código de acceso es " + code + ". Vence en " + validity.toMinutes()
                + " minutos. No lo compartas.";
        return body + "\n\nContacta al administrador: " + contactUrl;
    }
}
