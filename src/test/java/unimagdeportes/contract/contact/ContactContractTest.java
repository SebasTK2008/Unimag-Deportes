package unimagdeportes.contract.contact;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import unimagdeportes.support.AuthTestSupport;

class ContactContractTest extends AuthTestSupport {
    private static final String VALID_MESSAGE = "{\"subject\":\"Consulta\",\"message\":\"Necesito ayuda\"}";

    @Test
    void administratorCanContactWithoutReauthentication() throws Exception {
        String token = completeLogin("admin@unimagdalena.edu.co");
        int sentBefore = mail.sentCount.get();
        var response = send("POST", "/contact", VALID_MESSAGE, token);

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(body(response).get("message").asString())
                .isEqualTo("Tu mensaje fue enviado al área de deportes.");
        assertThat(mail.bodies.get("deportes@example.org"))
                .isEqualTo("De: admin@unimagdalena.edu.co\n\nNecesito ayuda");
        assertThat(mail.sentCount.get()).isEqualTo(sentBefore + 1);
    }

    @Test
    void rejectsRequestsWithoutAValidCompletedSession() throws Exception {
        error(send("POST", "/contact", VALID_MESSAGE, null), 401, "TOKEN_INVALID");
        error(send("POST", "/contact", VALID_MESSAGE, "invalid"), 401, "TOKEN_INVALID");
        assertThat(mail.sentCount.get()).isZero();

        String token = login("incomplete-contact@unimagdalena.edu.co");
        int sentBefore = mail.sentCount.get();
        error(send("POST", "/contact", VALID_MESSAGE, token), 403, "PROFILE_INCOMPLETE");
        assertThat(mail.sentCount.get()).isEqualTo(sentBefore);
        assertThat(mail.bodies).doesNotContainKey("deportes@example.org");
    }

    @Test
    void rejectsAnExpiredTokenWithoutSendingMail() throws Exception {
        String token = completeLogin("expired-contact@unimagdalena.edu.co");
        int sentBefore = mail.sentCount.get();
        clock.advance(Duration.ofHours(24));

        error(send("POST", "/contact", VALID_MESSAGE, token), 401, "TOKEN_EXPIRED");
        assertThat(mail.sentCount.get()).isEqualTo(sentBefore);
        assertThat(mail.bodies).doesNotContainKey("deportes@example.org");
    }

    @Test
    void rejectsMissingBlankAndNullRequiredFields() throws Exception {
        String token = completeLogin("required-contact@unimagdalena.edu.co");
        int sentBefore = mail.sentCount.get();
        for (String field : new String[]{"subject", "message"}) {
            String other = field.equals("subject") ? "message" : "subject";
            var values = new HashMap<String, Object>();
            values.put(other, "Contenido válido");
            var missing = send("POST", "/contact", json.writeValueAsString(values), token);
            error(missing, 400, "VALIDATION_ERROR");
            assertThat(body(missing).get("details").get("fields").get(field).asString()).isNotBlank();
            for (String invalid : new String[]{null, "", " \t"}) {
                values.put(field, invalid);
                var response = send("POST", "/contact", json.writeValueAsString(values), token);
                error(response, 400, "VALIDATION_ERROR");
                assertThat(body(response).get("details").get("fields").get(field).asString()).isNotBlank();
            }
        }
        assertThat(mail.sentCount.get()).isEqualTo(sentBefore);
        assertThat(mail.bodies).doesNotContainKey("deportes@example.org");
    }

    @Test
    void rejectsHeaderInjectionAndMalformedJson() throws Exception {
        String token = completeLogin("invalid-contact@unimagdalena.edu.co");
        int sentBefore = mail.sentCount.get();
        for (String subject : new String[]{"Consulta\rBcc: attacker@example.org", "Consulta\nOtro asunto"}) {
            var response = send("POST", "/contact",
                    json.writeValueAsString(Map.of("subject", subject, "message", "Contenido")), token);
            error(response, 400, "VALIDATION_ERROR");
            assertThat(body(response).get("details").get("fields").get("subject").asString()).isNotBlank();
        }
        error(send("POST", "/contact", "{", token), 400, "VALIDATION_ERROR");
        error(send("POST", "/contact", null, token), 400, "VALIDATION_ERROR");
        assertThat(mail.sentCount.get()).isEqualTo(sentBefore);
        assertThat(mail.bodies).doesNotContainKey("deportes@example.org");
    }
}
