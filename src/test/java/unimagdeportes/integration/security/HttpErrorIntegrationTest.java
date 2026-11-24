package unimagdeportes.integration.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import unimagdeportes.support.AuthTestSupport;

class HttpErrorIntegrationTest extends AuthTestSupport {
    @Test
    void unknownRoutesAndDisabledDevLoginAreNotServerErrors() throws Exception {
        String admin = completeLogin("admin@unimagdalena.edu.co");
        error(send("GET", "/unknown-route", null, admin), 404, "RESOURCE_NOT_FOUND");
        error(send("POST", "/dev/login", "{\"email\":\"user@unimagdalena.edu.co\",\"name\":\"User\"}", null),
                404, "RESOURCE_NOT_FOUND");
        error(send("POST", "/rules", null, admin), 405, "METHOD_NOT_ALLOWED");
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/auth/code"))
                .header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString("not-json")).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        error(response, 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void adminKeepsSharedCapabilitiesButCannotCreateReservations() throws Exception {
        String admin = completeLogin("admin@unimagdalena.edu.co");
        for (String route : new String[]{"/rules", "/quota/me", "/reservations/mine", "/spaces"}) {
            assertThat(send("GET", route, null, admin).statusCode()).isEqualTo(200);
        }
        error(send("POST", "/reservations", "{}", admin), 403, "FORBIDDEN");
    }
}
