package unimagdeportes.integration.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import unimagdeportes.model.reservation.ReservationStatus;
import unimagdeportes.support.ReservationTestSupport;

class ReservationMineIntegrationTest extends ReservationTestSupport {

    @Test
    void showsOnlyTheAuthenticatedUsersReservationsInEveryState() throws Exception {
        String owner = completeLogin("mine-owner@unimagdalena.edu.co");
        String other = completeLogin("mine-other@unimagdalena.edu.co");
        UUID ownerId = UUID.fromString(decoder.decode(owner).getSubject());
        UUID otherId = UUID.fromString(decoder.decode(other).getSubject());
        UUID space = space();

        insert(ownerId, space, "CONFIRMED", "2026-11-11T13:00:00Z", "2026-11-11T14:00:00Z");
        insert(ownerId, space, "CANCELLED_LATE", "2026-11-10T13:00:00Z", "2026-11-10T14:00:00Z");
        insert(otherId, space, "CONFIRMED", "2026-11-12T13:00:00Z", "2026-11-12T14:00:00Z");

        var mine = send("GET", "/reservations/mine", null, owner);
        assertThat(mine.statusCode()).isEqualTo(200);
        var reservations = body(mine).get("reservations");
        assertThat(reservations.size()).isEqualTo(2);
        assertThat(reservations.get(0).get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(reservations.get(1).get("status").asString()).isEqualTo("CANCELLED_LATE");
        for (var reservation : reservations) {
            assertThat(reservation.get("space").get("name").asString()).isEqualTo("Cancha");
        }
    }

    @Test
    void listsReservationsInEveryStatus() throws Exception {
        String owner = completeLogin("mine-all-states@unimagdalena.edu.co");
        UUID ownerId = UUID.fromString(decoder.decode(owner).getSubject());
        UUID space = space();
        var statuses = ReservationStatus.values();
        for (int day = 0; day < statuses.length; day++) {
            Instant start = Instant.parse("2026-11-01T13:00:00Z").plus(Duration.ofDays(day));
            insert(ownerId, space, statuses[day].name(), start.toString(), start.plus(Duration.ofHours(1)).toString());
        }

        var mine = send("GET", "/reservations/mine", null, owner);
        assertThat(mine.statusCode()).isEqualTo(200);
        var listed = new ArrayList<String>();
        body(mine).get("reservations").forEach(reservation -> listed.add(reservation.get("status").asString()));
        assertThat(listed).containsExactlyInAnyOrder(Arrays.stream(statuses).map(Enum::name).toArray(String[]::new));
    }

    @Test
    void suspendedUserCanStillSeeReservations() throws Exception {
        String user = completeLogin("mine-suspended@unimagdalena.edu.co");
        UUID userId = UUID.fromString(decoder.decode(user).getSubject());
        UUID space = space();
        insert(userId, space, "CONFIRMED", "2026-11-11T13:00:00Z", "2026-11-11T14:00:00Z");
        jdbc.update("update app_user set suspended_until = '2026-11-17T00:00:00Z' where id = ?", userId);

        var mine = send("GET", "/reservations/mine", null, user);
        assertThat(mine.statusCode()).isEqualTo(200);
        assertThat(body(mine).get("reservations").size()).isEqualTo(1);
    }

    private void insert(UUID userId, UUID spaceId, String status, String start, String end) {
        jdbc.update("insert into reservation (id,user_id,space_id,start_at,end_at,status,created_at,"
                        + "cancellation_threshold_minutes,checkin_opening_minutes) values (?, ?, ?, ?, ?, ?, ?, 60, 10)",
                UUID.randomUUID(), userId, spaceId, Timestamp.from(Instant.parse(start)), Timestamp.from(Instant.parse(end)),
                status, Timestamp.from(Instant.parse("2026-11-10T12:00:00Z")));
    }
}
