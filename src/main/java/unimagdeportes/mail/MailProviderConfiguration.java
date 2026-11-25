package unimagdeportes.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MailProviderConfiguration {
    public MailProviderConfiguration(@Value("${app.mail.provider:smtp}") String provider) {
        if (!"smtp".equals(provider) && !"brevo".equals(provider)) {
            throw new IllegalArgumentException("Proveedor de correo inválido: use smtp o brevo");
        }
    }
}
