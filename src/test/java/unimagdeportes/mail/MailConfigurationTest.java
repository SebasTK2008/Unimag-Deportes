package unimagdeportes.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class MailConfigurationTest {
    @Test
    void defaultsToLocalMailpitWithoutAuthenticationOrTls() throws IOException {
        var environment = mailEnvironment();
        assertThat(environment.getProperty("spring.mail.host")).isEqualTo("localhost");
        assertThat(environment.getProperty("spring.mail.port")).isEqualTo("1025");
        assertThat(environment.getProperty("spring.mail.properties[mail.smtp.auth]")).isEqualTo("false");
        assertThat(environment.getProperty("spring.mail.properties[mail.smtp.starttls.enable]")).isEqualTo("false");
        assertThat(environment.getProperty("spring.mail.properties[mail.smtp.starttls.required]")).isEqualTo("false");
        assertThat(environment.getProperty("app.mail.from-name")).isEmpty();
    }

    @Test
    void resolvesBrevoEnvironmentWithRequiredTlsAndDisplayName() throws IOException {
        var environment = mailEnvironment()
                .withProperty("MAIL_HOST", "smtp-relay.brevo.com")
                .withProperty("MAIL_PORT", "587")
                .withProperty("MAIL_USERNAME", "test@smtp-brevo.com")
                .withProperty("MAIL_PASSWORD", "test-only-smtp-key")
                .withProperty("MAIL_SMTP_AUTH", "true")
                .withProperty("MAIL_SMTP_STARTTLS", "true")
                .withProperty("MAIL_SMTP_STARTTLS_REQUIRED", "true")
                .withProperty("APP_MAIL_FROM", "deportes@example.org")
                .withProperty("APP_MAIL_FROM_NAME", "Unimag Deportes");
        assertThat(environment.getProperty("spring.mail.host")).isEqualTo("smtp-relay.brevo.com");
        assertThat(environment.getProperty("spring.mail.port")).isEqualTo("587");
        assertThat(environment.getProperty("spring.mail.username")).isEqualTo("test@smtp-brevo.com");
        assertThat(environment.getProperty("spring.mail.password")).isEqualTo("test-only-smtp-key");
        assertThat(environment.getProperty("spring.mail.properties[mail.smtp.auth]")).isEqualTo("true");
        assertThat(environment.getProperty("spring.mail.properties[mail.smtp.starttls.enable]")).isEqualTo("true");
        assertThat(environment.getProperty("spring.mail.properties[mail.smtp.starttls.required]")).isEqualTo("true");
        assertThat(environment.getProperty("app.mail.from")).isEqualTo("deportes@example.org");
        assertThat(environment.getProperty("app.mail.from-name")).isEqualTo("Unimag Deportes");
    }

    private MockEnvironment mailEnvironment() throws IOException {
        var environment = new MockEnvironment();
        for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"))) {
            environment.getPropertySources().addLast(source);
        }
        return environment;
    }
}
