package unimagdeportes.integration.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import unimagdeportes.support.AuthTestSupport;

class HealthIntegrationTest extends AuthTestSupport {
    @Autowired private ApplicationContext context;

    @Test
    void healthProbesArePublicAndDoNotDiscloseDependencies() throws Exception {
        for (String path : new String[]{"/health", "/health/liveness", "/health/readiness"}) {
            var response = send("GET", path, null, null);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThat(body(response).get("status").asString()).isEqualTo("UP");
            assertThat(response.body()).doesNotContain("components", "jdbc", "password", "details");
        }
        assertThat(send("GET", "/health/db", null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/beans", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void refusingTrafficFailsReadinessButDoesNotFailLiveness() throws Exception {
        AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
        try {
            var response = send("GET", "/health/readiness", null, null);
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(body(response).get("status").asString()).isEqualTo("OUT_OF_SERVICE");
            assertThat(send("GET", "/health/liveness", null, null).statusCode()).isEqualTo(200);
        } finally {
            AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
        }
    }

    @Test
    void incompleteProfileDoesNotBlockHealthProbes() throws Exception {
        String token = login("health-probe@unimagdalena.edu.co");
        assertThat(send("GET", "/health/readiness", null, token).statusCode()).isEqualTo(200);
    }
}
