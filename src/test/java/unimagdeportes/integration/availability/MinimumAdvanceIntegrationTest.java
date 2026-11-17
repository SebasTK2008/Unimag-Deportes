package unimagdeportes.integration.availability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import unimagdeportes.support.ReservationTestSupport;

@TestPropertySource(properties = "app.rules.min-advance-minutes=30")
class MinimumAdvanceIntegrationTest extends ReservationTestSupport {
    @Test
    void calendarAndCreationAgreeBeforeAndAtMinimumAdvanceBoundary() throws Exception {
        String user = completeLogin("advance@unimagdalena.edu.co");
        var space = space();
        clock.set(Instant.parse("2026-11-11T12:40:00Z"));
        String path = "/spaces/" + space + "/availability?date=2026-11-11";
        var calendar = body(send("GET", path, null, user));
        assertThat(calendar.get("slots").get(1).get("bookable").asBoolean()).isFalse();
        String payload = """
                {"spaceId":"%s","startAt":"2026-11-11T08:00:00-05:00","slots":1}
                """.formatted(space);
        error(send("POST", "/reservations", payload, user), 409, "OUTSIDE_BOOKING_WINDOW");
        clock.set(Instant.parse("2026-11-11T12:30:00Z"));
        calendar = body(send("GET", path, null, user));
        assertThat(calendar.get("slots").get(1).get("bookable").asBoolean()).isTrue();
        assertThat(send("POST", "/reservations", payload, user).statusCode()).isEqualTo(201);
    }
}
