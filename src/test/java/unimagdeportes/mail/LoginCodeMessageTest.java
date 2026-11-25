package unimagdeportes.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailPreparationException;

@ExtendWith(OutputCaptureExtension.class)
class LoginCodeMessageTest {
    @Test
    void refusesToRenderWithoutContact(CapturedOutput output) {
        assertThatThrownBy(() -> new LoginCodeMessage(" ").render("123456", Duration.ofMinutes(15)))
                .isInstanceOf(MailPreparationException.class);
        assertThat(output.getAll()).contains("MAIL_CONTACT_NOT_CONFIGURED", "APP_MAIL_CONTACT_URL")
                .doesNotContain("123456");
    }

    @Test
    void includesContactWhenConfigured() {
        assertThat(new LoginCodeMessage("https://client.example/contact").render("123456", Duration.ofMinutes(15)))
                .contains("123456", "15 minutos", "No lo compartas", "https://client.example/contact");
    }

    @Test
    void showsLargeCenteredSelectableCodeAndPreservesLeadingZerosInBothAlternatives() {
        var body = new LoginCodeMessage("https://client.example/contact").compose("001234", Duration.ofMinutes(7));
        assertThat(body.text()).contains("001234", "7 minutos", "No lo compartas");
        assertThat(body.html()).contains("001234", "font-size:40px", "text-align:center", "7 minutos",
                "No lo compartas", "https://client.example/contact").doesNotContain("<img");
    }
}
