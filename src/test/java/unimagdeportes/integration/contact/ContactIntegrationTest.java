package unimagdeportes.integration.contact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import unimagdeportes.support.AuthTestSupport;

class ContactIntegrationTest extends AuthTestSupport {
    @Test
    void authenticatedUserCanSendMessageWithoutPersistingIt() throws Exception {
        String token = completeLogin("contact@unimagdalena.edu.co");
        var response = send("POST", "/contact", "{\"subject\":\"Consulta\",\"message\":\"Mensaje de prueba\"}", token);
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(body(response).get("message").asString()).isEqualTo("Tu mensaje fue enviado al área de deportes.");
        assertThat(mail.bodies.get("deportes@example.org"))
                .contains("contact@unimagdalena.edu.co", "Mensaje de prueba");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where detail like '%Mensaje de prueba%'", Integer.class))
                .isZero();
    }

    @Test
    void rejectsAnonymousInvalidAndHeaderInjectionRequests() throws Exception {
        assertThat(send("POST", "/contact", "{\"subject\":\"x\",\"message\":\"y\"}", null).statusCode()).isEqualTo(401);
        String token = completeLogin("validation@unimagdalena.edu.co");
        assertThat(send("POST", "/contact", "{\"subject\":\" \",\"message\":\"y\"}", token).statusCode()).isEqualTo(400);
        var injection = send("POST", "/contact", "{\"subject\":\"x\\ny\",\"message\":\"y\"}", token);
        error(injection, 400, "VALIDATION_ERROR");
        assertThat(body(injection).get("details").get("fields").get("subject").asString()).isNotBlank();
        assertThat(mail.bodies).doesNotContainKey("deportes@example.org");
    }

    @Test
    void reportsDeliveryFailureWithoutExposingSmtpDetails() throws Exception {
        String token = completeLogin("failure@unimagdalena.edu.co");
        mail.failing = true;
        var response = send("POST", "/contact", "{\"subject\":\"x\",\"message\":\"y\"}", token);
        error(response, 503, "MAIL_DELIVERY_FAILED");
        assertThat(response.body()).doesNotContain("smtp-test-secret");
    }
}
