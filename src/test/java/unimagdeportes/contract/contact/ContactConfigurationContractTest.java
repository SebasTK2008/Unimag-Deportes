package unimagdeportes.contract.contact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import unimagdeportes.support.AuthTestSupport;

@TestPropertySource(properties = "app.mail.contact-recipient= ")
class ContactConfigurationContractTest extends AuthTestSupport {
    @Test
    void missingRecipientReturnsServiceUnavailableWithoutSendingMail() throws Exception {
        String token = completeLogin("unconfigured-contact@unimagdalena.edu.co");
        int sentBefore = mail.sentCount.get();

        var response = send("POST", "/contact", "{\"subject\":\"Consulta\",\"message\":\"Ayuda\"}", token);

        error(response, 503, "CONTACT_NOT_CONFIGURED");
        assertThat(body(response).get("message").asString()).isEqualTo("El canal de contacto aún no está disponible.");
        assertThat(mail.sentCount.get()).isEqualTo(sentBefore);
    }
}
