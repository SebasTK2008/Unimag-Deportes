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

    public MailBody compose(String code, Duration validity) {
        String text = render(code, validity);
        String html = MailLayout.render("Tu código de acceso",
                MailLayout.paragraph("Ingresa este código en Unimag Deportes para continuar.")
                        + "<p style=\"text-align:center;margin:28px 0;padding:20px 8px;background:#f2f5f8;"
                        + "font-family:'Courier New',monospace;font-size:40px;font-weight:bold;"
                        + "line-height:1.3;color:#20395f;\">" + MailLayout.escape(code) + "</p>"
                        + MailLayout.paragraph("Vence en " + validity.toMinutes() + " minutos. No lo compartas.")
                        + MailLayout.contact(contactUrl));
        return new MailBody(text, html);
    }
}
