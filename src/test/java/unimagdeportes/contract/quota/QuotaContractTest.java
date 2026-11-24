package unimagdeportes.contract.quota;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import unimagdeportes.support.ReservationTestSupport;

class QuotaContractTest extends ReservationTestSupport {

    @Test
    void returnsTheWeeklyQuotaOfTheAuthenticatedUser() throws Exception {
        String user = completeLogin("quota-contract@unimagdalena.edu.co");
        UUID space = space();
        assertThat(send("POST", "/reservations",
                "{\"spaceId\":\"%s\",\"startAt\":\"2026-11-11T08:00:00-05:00\",\"slots\":2}".formatted(space), user)
                .statusCode()).isEqualTo(201);

        var response = send("GET", "/quota/me", null, user);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body(response)).isEqualTo(json.readTree("""
                {"weekStart":"2026-11-09T00:00:00-05:00","weekEnd":"2026-11-16T00:00:00-05:00",
                 "limitHours":4,"usedHours":2,"availableHours":2,"resetAt":"2026-11-16T00:00:00-05:00"}
                """));
    }

    @Test
    void isAvailableToAdministratorsAndSuspendedUsers() throws Exception {
        String admin = completeLogin("admin@unimagdalena.edu.co");
        assertThat(send("GET", "/quota/me", null, admin).statusCode()).isEqualTo(200);

        String suspended = completeLogin("quota-suspended@unimagdalena.edu.co");
        jdbc.update("update app_user set suspended_until = ? where id = ?",
                java.sql.Timestamp.from(START.plus(Duration.ofDays(7))), userId(suspended));
        assertThat(send("GET", "/quota/me", null, suspended).statusCode()).isEqualTo(200);
    }

    @Test
    void enforcesAuthenticationAndProfile() throws Exception {
        error(send("GET", "/quota/me", null, null), 401, "TOKEN_INVALID");
        error(send("GET", "/quota/me", null, "invalid-token"), 401, "TOKEN_INVALID");
        String incomplete = login("quota-incomplete@unimagdalena.edu.co");
        error(send("GET", "/quota/me", null, incomplete), 403, "PROFILE_INCOMPLETE");
        String user = completeLogin("quota-expiring@unimagdalena.edu.co");
        clock.advance(Duration.ofHours(24));
        error(send("GET", "/quota/me", null, user), 401, "TOKEN_EXPIRED");
    }
}
