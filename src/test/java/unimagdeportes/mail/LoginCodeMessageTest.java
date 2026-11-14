package unimagdeportes.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailPreparationException;

class LoginCodeMessageTest {
    @Test
    void refusesToRenderWithoutContact() {
        assertThatThrownBy(() -> new LoginCodeMessage(" ").render("123456", Duration.ofMinutes(15)))
                .isInstanceOf(MailPreparationException.class);
    }

    @Test
    void includesContactWhenConfigured() {
        assertThat(new LoginCodeMessage("https://client.example/contact").render("123456", Duration.ofMinutes(15)))
                .contains("123456", "15 minutos", "No lo compartas", "https://client.example/contact");
    }
}
