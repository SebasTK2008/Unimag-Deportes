package unimagdeportes.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.json.JsonMapper;

class MailProviderConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory().setConversionService(
                    org.springframework.boot.convert.ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(MailProviderConfiguration.class, BrevoHttpMailSender.class, SmtpMailSender.class)
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build());

    @Test
    void defaultsToSmtpForExistingLocalEnvironments() {
        runner.withBean(JavaMailSender.class, () -> mock(JavaMailSender.class)).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(MailSender.class).hasSingleBean(SmtpMailSender.class);
            assertThat(context).doesNotHaveBean(BrevoHttpMailSender.class);
        });
    }

    @Test
    void selectsBrevoWithoutAnySmtpBeanOrCredentials() {
        brevo().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(MailSender.class).hasSingleBean(BrevoHttpMailSender.class);
            assertThat(context).doesNotHaveBean(SmtpMailSender.class).doesNotHaveBean(JavaMailSender.class);
        });
    }

    @Test
    void rejectsUnknownProviders() {
        runner.withPropertyValues("app.mail.provider=unknown").run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"app.mail.brevo.api-key=", "app.mail.from=", "app.mail.from=invalid",
            "app.mail.from=Name <deportes@example.org>", "app.mail.from-name=Bad\r\nName",
            "app.mail.brevo.connect-timeout=PT0S", "app.mail.brevo.request-timeout=PT61S"})
    void rejectsInvalidBrevoConfiguration(String property) {
        brevo().withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner brevo() {
        return runner.withPropertyValues("app.mail.provider=brevo", "app.mail.brevo.api-key=test-key",
                "app.mail.from=deportes@example.org");
    }
}
