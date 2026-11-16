package unimagdeportes.contract.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import unimagdeportes.support.ReservationTestSupport;

class ReservationMineContractTest extends ReservationTestSupport {

    @Test
    void listsOwnReservationsWithStateAndEquipment() throws Exception {
        String user = completeLogin("mine@unimagdalena.edu.co");
        UUID space = space();
        var item = equipment("Balón de voleibol", 5);
        var created = send("POST", "/reservations", """
                {"spaceId":"%s","startAt":"2026-11-11T08:00:00-05:00","slots":1,
                 "equipment":[{"equipmentId":"%s","quantity":1}]}
                """.formatted(space, item.id()), user);
        assertThat(created.statusCode()).isEqualTo(201);

        var mine = send("GET", "/reservations/mine", null, user);
        assertThat(mine.statusCode()).isEqualTo(200);
        var reservations = body(mine).get("reservations");
        assertThat(reservations.isArray()).isTrue();
        assertThat(reservations.size()).isEqualTo(1);
        var reservation = reservations.get(0);
        assertThat(reservation.get("space").get("id").asString()).isEqualTo(space.toString());
        assertThat(reservation.get("space").get("name").asString()).isEqualTo("Cancha");
        assertThat(reservation.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(reservation.get("startAt").asString()).isEqualTo("2026-11-11T08:00:00-05:00");
        assertThat(reservation.get("endAt").asString()).isEqualTo("2026-11-11T09:00:00-05:00");
        assertThat(reservation.get("checkedInAt").isNull()).isTrue();
        assertThat(reservation.get("equipment").size()).isEqualTo(1);
        assertThat(reservation.get("equipment").get(0).get("equipmentId").asString()).isEqualTo(item.id().toString());
        assertThat(reservation.get("equipment").get(0).get("name").asString()).isEqualTo(item.name());
        assertThat(reservation.get("equipment").get(0).get("quantity").asInt()).isEqualTo(1);
    }

    @Test
    void returnsEmptyEquipmentArrayWhenNoneRequested() throws Exception {
        String user = completeLogin("mine-empty@unimagdalena.edu.co");
        UUID space = space();
        assertThat(send("POST", "/reservations",
                "{\"spaceId\":\"%s\",\"startAt\":\"2026-11-11T08:00:00-05:00\",\"slots\":1}".formatted(space), user)
                .statusCode()).isEqualTo(201);

        var mine = send("GET", "/reservations/mine", null, user);
        assertThat(mine.statusCode()).isEqualTo(200);
        var equipment = body(mine).get("reservations").get(0).get("equipment");
        assertThat(equipment.isArray()).isTrue();
        assertThat(equipment.size()).isZero();
    }

    @Test
    void enforcesAuthenticationAndProfile() throws Exception {
        error(send("GET", "/reservations/mine", null, null), 401, "TOKEN_INVALID");
        error(send("GET", "/reservations/mine", null, "invalid-token"), 401, "TOKEN_INVALID");
        String incomplete = login("incomplete@unimagdalena.edu.co");
        error(send("GET", "/reservations/mine", null, incomplete), 403, "PROFILE_INCOMPLETE");
        String user = completeLogin("mine-expiring@unimagdalena.edu.co");
        clock.advance(Duration.ofHours(24));
        error(send("GET", "/reservations/mine", null, user), 401, "TOKEN_EXPIRED");
    }
}
