package unimagdeportes.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import unimagdeportes.dto.mail.Receipt;

class ReceiptMessageTest {
    @Test
    void confirmationIncludesRulesAndCheckInInformationInBothAlternativesAndEscapesHtml() {
        var receipt = new Receipt("test@unimagdalena.edu.co", "Persona <script>alert(1)</script>", "Cancha & pista",
                Instant.parse("2026-11-25T19:00:00Z"), Instant.parse("2026-11-25T20:00:00Z"), List.of(),
                "Con 90 minutos de anticipación se devuelven las horas.", "https://client.example/contact?a=1&b=2");
        var body = ReceiptMessage.render(receipt);
        assertThat(body.text()).contains("Reserva confirmada", "90 minutos", "25/11/2026 14:00",
                ReceiptMessage.CHECK_IN_INFORMATION);
        assertThat(body.html()).contains("Reserva confirmada", "90 minutos", "Cancha &amp; pista", "&lt;script&gt;",
                ReceiptMessage.CHECK_IN_INFORMATION).doesNotContain("<script>");
    }

    @Test
    void configuredContactCannotInjectHtmlOrCreateAnExecutableLink() {
        assertThat(MailLayout.contact("javascript:alert(1)")).doesNotContain("href=");
        assertThat(MailLayout.contact("https://example.org/?x=\" onclick=\"bad"))
                .doesNotContain("href=").doesNotContain(" onclick=\"");
        assertThat(MailLayout.paragraph("<img src=x onerror=alert(1)>")).doesNotContain("<img");
    }
}
