package unimagdeportes.integration.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import unimagdeportes.scheduler.ReservationLifecycleJob;
import unimagdeportes.support.ReservationTestSupport;

class ParameterConsumersIntegrationTest extends ReservationTestSupport {
    @Autowired ReservationLifecycleJob lifecycle;

    @Test
    void publishedQuotaAndThresholdAreUsedByNewOperations() throws Exception {
        String admin = administrator();
        String user = completeLogin("parameter-consumer@unimagdalena.edu.co");
        UUID space = space();
        assertThat(send("PUT", "/admin/parameters", """
                {"quota":{"weeklyHours":1},"sanctions":{"noShowsForSuspension":1}}
                """, admin).statusCode()).isEqualTo(200);
        String request = """
                {"spaceId":"%s","startAt":"2026-11-11T08:00:00-05:00","slots":%d}
                """;
        error(send("POST", "/reservations", request.formatted(space, 2), user), 409, "QUOTA_EXCEEDED");
        assertThat(send("POST", "/reservations", request.formatted(space, 1), user).statusCode()).isEqualTo(201);
        UUID id = userId(user);
        fixtureReservation(id, space(), "CONFIRMED", START.minus(Duration.ofHours(2)).minusSeconds(1),
                START.minus(Duration.ofHours(1)).minusSeconds(1));
        lifecycle.closeFinishedReservations();
        assertThat(jdbc.queryForObject("select suspended_until is not null from app_user where id=?",
                Boolean.class, id)).isTrue();
        assertThat(body(send("GET", "/reservations/mine", null, user)).get("reservations").valueStream()
                .anyMatch(r -> r.get("status").asString().equals("CANCELLED_BY_SUSPENSION"))).isTrue();
    }
}
