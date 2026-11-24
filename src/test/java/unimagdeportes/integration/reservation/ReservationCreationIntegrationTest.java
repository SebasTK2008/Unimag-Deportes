package unimagdeportes.integration.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import unimagdeportes.support.RulesTestSupport;

class ReservationCreationIntegrationTest extends RulesTestSupport {
    @Test
    void dailyLimitAccumulatesAcrossSpacesButAllowsConsecutiveHoursAndAnotherDay() throws Exception {
        String user = completeLogin("daily@unimagdalena.edu.co");
        UUID first = space(), second = space();
        assertThat(send("POST", "/reservations", request(first, "2026-11-11T07:00:00-05:00", 1), user)
                .statusCode()).isEqualTo(201);
        assertThat(send("POST", "/reservations", request(second, "2026-11-11T08:00:00-05:00", 1), user)
                .statusCode()).isEqualTo(201);
        var rejected = send("POST", "/reservations", request(first, "2026-11-11T09:00:00-05:00", 1), user);
        error(rejected, 409, "DAILY_QUOTA_EXCEEDED");
        assertThat(body(rejected).get("details").get("usedHours").asDouble()).isEqualTo(2);
        assertThat(send("POST", "/reservations", request(first, "2026-11-12T08:00:00-05:00", 2), user)
                .statusCode()).isEqualTo(201);
        error(send("POST", "/reservations", request(first, "2026-11-13T08:00:00-05:00", 1), user),
                409, "QUOTA_EXCEEDED");
    }

    @Test
    void dailyLimitUsesTheSameStatesAsWeeklyQuota() throws Exception {
        for (var status : unimagdeportes.model.reservation.ReservationStatus.values()) {
            String user = completeLogin("daily-" + status.name().toLowerCase() + "@unimagdalena.edu.co");
            UUID userId = UUID.fromString(decoder.decode(user).getSubject());
            UUID space = space();
            jdbc.update("insert into reservation (id,user_id,space_id,start_at,end_at,status,created_at,"
                            + "cancellation_threshold_minutes,checkin_opening_minutes) values (?, ?, ?, "
                            + "'2026-11-11T12:00:00Z', '2026-11-11T14:00:00Z', ?, now(), 60, 10)",
                    UUID.randomUUID(), userId, space, status.name());
            var response = send("POST", "/reservations", request(space, "2026-11-11T09:00:00-05:00", 1), user);
            if (status.countsForQuota()) error(response, 409, "DAILY_QUOTA_EXCEEDED");
            else assertThat(response.statusCode()).isEqualTo(201);
        }
    }

    @Test
    void concurrentRequestsCannotExceedTheDailyLimit() throws Exception {
        String user = completeLogin("daily-concurrent@unimagdalena.edu.co");
        UUID first = space(), second = space();
        assertThat(send("POST", "/reservations", request(first, "2026-11-11T07:00:00-05:00", 1), user)
                .statusCode()).isEqualTo(201);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return send("POST", "/reservations",
                    request(first, "2026-11-11T08:00:00-05:00", 1), user); });
            var b = executor.submit(() -> { start.await(); return send("POST", "/reservations",
                    request(second, "2026-11-11T09:00:00-05:00", 1), user); });
            start.countDown();
            var responses = java.util.List.of(a.get(), b.get());
            assertThat(responses.stream().map(java.net.http.HttpResponse::statusCode))
                    .containsExactlyInAnyOrder(201, 409);
            error(responses.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow(),
                    409, "DAILY_QUOTA_EXCEEDED");
        }
    }

    private String request(UUID space, String start, int slots) {
        return "{\"spaceId\":\"%s\",\"startAt\":\"%s\",\"slots\":%d}".formatted(space, start, slots);
    }

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
        jdbc.update("update space set closing_time = '23:00' where id = ?", space);
        String path = "/spaces/" + space + "/availability?date=";
        assertThat(send("GET", path + "2026-11-17", null, user).statusCode()).isEqualTo(200);
        error(send("GET", path + "2026-11-18", null, user), 400, "OUTSIDE_CALENDAR_HORIZON");
        String request = """
                {"spaceId":"%s","startAt":"%s","slots":1}
                """;
        assertThat(send("POST", "/reservations",
                request.formatted(space, "2026-11-17T22:00:00-05:00"), user).statusCode()).isEqualTo(201);
        error(send("POST", "/reservations",
                request.formatted(space, "2026-11-18T08:00:00-05:00"), user), 409, "OUTSIDE_BOOKING_WINDOW");
    }

    private UUID space() {
        UUID id = UUID.randomUUID();
        UUID preview = UUID.randomUUID();
        UUID panorama = UUID.randomUUID();
        jdbc.update("insert into space_image (id,content_type,storage_key,size_bytes) "
                + "values (?, 'image/png', ?, 1), (?, 'image/png', ?, 1)",
                preview, "fixtures/" + preview, panorama, "fixtures/" + panorama);
        jdbc.update("insert into space (id,name,capacity,opening_time,closing_time,status,preview_image_id,panorama_image_id)"
                + " values (?, 'Cancha', 10, '07:00', '11:00', 'ACTIVE', ?, ?)", id, preview, panorama);
        return id;
    }
}
