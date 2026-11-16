package unimagdeportes.contract.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import unimagdeportes.support.ReservationTestSupport;

class OccupancyContractTest extends ReservationTestSupport {

    @Test
    void returnsTheOccupancyBySpaceAndSlot() throws Exception {
        String admin = completeLogin("admin@unimagdalena.edu.co");
        UUID space = space("Cancha de voleibol 1", "VOLLEYBALL");
        UUID user = fixtureUser("occupancy-contract");
        UUID reservation = fixtureReservation(user, space, "CONFIRMED",
                Instant.parse("2026-11-11T13:00:00Z"), Instant.parse("2026-11-11T14:00:00Z"));
        fixtureReservation(user, space, "CANCELLED_ON_TIME",
                Instant.parse("2026-11-11T15:00:00Z"), Instant.parse("2026-11-11T16:00:00Z"));
        jdbc.update("insert into institutional_block (id, space_id, type, reason, start_at, end_at) "
                + "values (?, ?, 'EVENT', 'Torneo', ?, ?)", UUID.randomUUID(), space,
                Timestamp.from(Instant.parse("2026-11-11T14:00:00Z")), Timestamp.from(Instant.parse("2026-11-11T15:00:00Z")));

        var response = send("GET", "/admin/occupancy?date=2026-11-11&discipline=VOLLEYBALL&spaceId=" + space, null, admin);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body(response)).isEqualTo(json.readTree("""
                {"date":"2026-11-11","spaces":[{"spaceId":"%s","spaceName":"Cancha de voleibol 1","slots":[
                  {"startAt":"2026-11-11T07:00:00-05:00","endAt":"2026-11-11T08:00:00-05:00","status":"FREE","reservationId":null},
                  {"startAt":"2026-11-11T08:00:00-05:00","endAt":"2026-11-11T09:00:00-05:00","status":"OCCUPIED","reservationId":"%s"},
                  {"startAt":"2026-11-11T09:00:00-05:00","endAt":"2026-11-11T10:00:00-05:00","status":"BLOCKED","reservationId":null},
                  {"startAt":"2026-11-11T10:00:00-05:00","endAt":"2026-11-11T11:00:00-05:00","status":"FREE","reservationId":null}
                ]}]}
                """.formatted(space, reservation)));
    }

    @Test
    void rejectsMissingOrInvalidFilters() throws Exception {
        String admin = completeLogin("admin@unimagdalena.edu.co");
        var missing = send("GET", "/admin/occupancy", null, admin);
        error(missing, 400, "VALIDATION_ERROR");
        assertThat(body(missing).get("details").get("fields").get("date").asString()).isEqualTo("valor inválido");
        error(send("GET", "/admin/occupancy?date=11-11-2026", null, admin), 400, "INVALID_DATE");
        error(send("GET", "/admin/occupancy?date=2026-11-11&discipline=CHESS", null, admin), 400, "VALIDATION_ERROR");
        error(send("GET", "/admin/occupancy?date=2026-11-11&spaceId=not-a-uuid", null, admin), 400, "VALIDATION_ERROR");
    }

    @Test
    void requiresTheCurrentAdministratorRole() throws Exception {
        error(send("GET", "/admin/occupancy?date=2026-11-11", null, null), 401, "TOKEN_INVALID");
        String user = completeLogin("occupancy-user@unimagdalena.edu.co");
        error(send("GET", "/admin/occupancy?date=2026-11-11", null, user), 403, "FORBIDDEN");
        String admin = completeLogin("admin@unimagdalena.edu.co");
        jdbc.update("update app_user set role = 'USER' where email = 'admin@unimagdalena.edu.co'");
        error(send("GET", "/admin/occupancy?date=2026-11-11", null, admin), 403, "FORBIDDEN");
        clock.advance(Duration.ofHours(24));
        error(send("GET", "/admin/occupancy?date=2026-11-11", null, user), 401, "TOKEN_EXPIRED");
    }
}
