package unimagdeportes.integration.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import unimagdeportes.support.RulesTestSupport;

class ReservationCreationIntegrationTest extends RulesTestSupport {
    @Test
    void createsReservationAndRejectsConflictingSlot() throws Exception {
        String first = completeLogin("reserve-one@unimagdalena.edu.co");
        String second = completeLogin("reserve-two@unimagdalena.edu.co");
        UUID space = space();
        String request = """
                {"spaceId":"%s","startAt":"2026-11-11T08:00:00-05:00","slots":2}
                """.formatted(space);

        var created = send("POST", "/reservations", request, first);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(body(created).get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(body(created).get("equipment").isArray()).isTrue();
        error(send("POST", "/reservations", request, second), 409, "SLOT_TAKEN");
        assertThat(jdbc.queryForObject("select count(*) from reservation where space_id = ?", Integer.class, space))
                .isEqualTo(1);
    }

    @Test
    void reservesEquipmentAndRollsBackWhenStockIsInsufficient() throws Exception {
        String first = completeLogin("reserve-equipment@unimagdalena.edu.co");
        String second = completeLogin("reserve-equipment-two@unimagdalena.edu.co");
        UUID firstSpace = space();
        UUID secondSpace = space();
        UUID item = UUID.randomUUID();
        jdbc.update("insert into equipment (id, name, stock, status) values (?, ?, 1, 'AVAILABLE')", item,
                "Item " + item);
        String request = """
                {"spaceId":"%s","startAt":"2026-11-11T08:00:00-05:00","slots":1,
                 "equipment":[{"equipmentId":"%s","quantity":1}]}
                """;

        var created = send("POST", "/reservations", request.formatted(firstSpace, item), first);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(body(created).get("equipment").get(0).get("equipmentId").asString()).isEqualTo(item.toString());
        error(send("POST", "/reservations", request.formatted(secondSpace, item), second), 409, "EQUIPMENT_UNAVAILABLE");
        assertThat(jdbc.queryForObject("select count(*) from reservation where space_id = ?", Integer.class, secondSpace))
                .isZero();
    }

    @Test
    void simultaneousRequestsCannotReserveTheSameSlot() throws Exception {
        String first = completeLogin("concurrent-one@unimagdalena.edu.co");
        String second = completeLogin("concurrent-two@unimagdalena.edu.co");
        UUID space = space();
        String request = """
                {"spaceId":"%s","startAt":"2026-11-11T08:00:00-05:00","slots":1}
                """.formatted(space);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return send("POST", "/reservations", request, first); });
            var b = executor.submit(() -> { start.await(); return send("POST", "/reservations", request, second); });
            start.countDown();
            assertThat(java.util.List.of(a.get().statusCode(), b.get().statusCode()))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from reservation where space_id = ?", Integer.class, space))
                .isEqualTo(1);
    }

    @Test
    void enforcesQuotaAndRejectsSuspendedUser() throws Exception {
        String user = completeLogin("reserve-quota@unimagdalena.edu.co");
        UUID userId = UUID.fromString(decoder.decode(user).getSubject());
        UUID space = space();
        String twoSlots = """
                {"spaceId":"%s","startAt":"2026-11-11T08:00:00-05:00","slots":2}
                """.formatted(space);
        jdbc.update("insert into reservation (id,user_id,space_id,start_at,end_at,status,created_at,"
                        + "cancellation_threshold_minutes,checkin_opening_minutes) values (?, ?, ?, "
                        + "'2026-11-12T13:00:00Z', '2026-11-12T16:00:00Z', 'COMPLETED', "
                        + "'2026-11-10T12:00:00Z', 60, 10)", UUID.randomUUID(), userId, space);
        var rejected = send("POST", "/reservations", twoSlots, user);
        error(rejected, 409, "QUOTA_EXCEEDED");
        assertThat(body(rejected).get("details").get("availableHours").asDouble()).isEqualTo(1.0);
        jdbc.update("update app_user set suspended_until = '2026-11-17T00:00:00Z' where id = ?", userId);
        error(send("POST", "/reservations", twoSlots, user), 403, "SUSPENDED");
    }

    @Test
    void calendarAndReservationAcceptTheSameLastDate() throws Exception {
        String user = completeLogin("reservation-horizon@unimagdalena.edu.co");
        UUID space = space();
        String path = "/spaces/" + space + "/availability?date=";
        assertThat(send("GET", path + "2026-11-18", null, user).statusCode()).isEqualTo(200);
        error(send("GET", path + "2026-11-19", null, user), 400, "OUTSIDE_CALENDAR_HORIZON");
        String request = """
                {"spaceId":"%s","startAt":"%s","slots":1}
                """;
        assertThat(send("POST", "/reservations",
                request.formatted(space, "2026-11-18T10:00:00-05:00"), user).statusCode()).isEqualTo(201);
        error(send("POST", "/reservations",
                request.formatted(space, "2026-11-19T08:00:00-05:00"), user), 409, "OUTSIDE_BOOKING_WINDOW");
    }

    private UUID space() {
        UUID id = UUID.randomUUID();
        UUID preview = UUID.randomUUID();
        UUID panorama = UUID.randomUUID();
        jdbc.update("insert into space_image (id,content_type,content) values (?, 'image/png', ?), (?, 'image/png', ?)",
                preview, new byte[]{1}, panorama, new byte[]{1});
        jdbc.update("insert into space (id,name,capacity,opening_time,closing_time,status,preview_image_id,panorama_image_id)"
                + " values (?, 'Cancha', 10, '07:00', '11:00', 'ACTIVE', ?, ?)", id, preview, panorama);
        return id;
    }
}
