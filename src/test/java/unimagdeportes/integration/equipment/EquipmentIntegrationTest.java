package unimagdeportes.integration.equipment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import unimagdeportes.support.RulesTestSupport;

class EquipmentIntegrationTest extends RulesTestSupport {
    @Test
    void catalogRequiresAdministratorAndPreservesPartialUpdates() throws Exception {
        String admin = administrator();
        String user = completeLogin("equipment-user@unimagdalena.edu.co");
        String name = "Balón " + UUID.randomUUID();
        String request = """
                {"name":"%s","discipline":"VOLLEYBALL","stock":3}
                """.formatted(name);
        error(send("POST", "/admin/equipment", request, user), 403, "FORBIDDEN");
        var created = send("POST", "/admin/equipment", request, admin);
        assertThat(created.statusCode()).isEqualTo(201);
        String id = body(created).get("id").asString();
        error(send("POST", "/admin/equipment", request, admin), 409, "EQUIPMENT_NAME_ALREADY_EXISTS");
        UUID other = createItem(admin, 1);
        error(send("PUT", "/admin/equipment/" + other, "{\"name\":\"" + name + "\"}", admin),
                409, "EQUIPMENT_NAME_ALREADY_EXISTS");
        var updated = send("PUT", "/admin/equipment/" + id, "{\"discipline\":null}", admin);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(body(updated).get("discipline").isNull()).isTrue();
        assertThat(body(updated).get("name").asString()).isEqualTo(name);
        assertThat(body(updated).get("stock").asInt()).isEqualTo(3);
        for (String field : java.util.List.of("name", "stock", "status")) {
            error(send("PUT", "/admin/equipment/" + id, "{\"" + field + "\":null}", admin),
                    400, "VALIDATION_ERROR");
        }
        error(send("PUT", "/admin/equipment/" + id, "{\"stock\":1}", user), 403, "FORBIDDEN");
        error(send("PUT", "/admin/equipment/" + id, "{\"stock\":-1}", admin), 400, "VALIDATION_ERROR");
        assertThat(send("GET", "/equipment", null, user).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/equipment", null, null).statusCode()).isEqualTo(401);
        error(send("GET", "/equipment/availability?startAt=2026-11-11T15:00:00Z"
                + "&endAt=2026-11-11T13:00:00Z", null, user), 400, "VALIDATION_ERROR");
    }

    @Test
    void rejectsNonIntegerStockAndQuantitiesWithoutChangingState() throws Exception {
        String admin = administrator();
        String user = completeLogin("equipment-numeric@unimagdalena.edu.co");
        UUID item = createItem(admin, 3);
        UUID space = space();
        for (String value : java.util.List.of("1.5", "\"2\"", "2147483648")) {
            error(send("POST", "/admin/equipment", "{\"name\":\"Invalid\",\"stock\":" + value + "}", admin),
                    400, "VALIDATION_ERROR");
            error(send("PUT", "/admin/equipment/" + item, "{\"stock\":" + value + "}", admin),
                    400, "VALIDATION_ERROR");
            String request = """
                    {"spaceId":"%s","startAt":"2026-11-11T08:00:00-05:00","slots":1,
                     "equipment":[{"equipmentId":"%s","quantity":%s}]}
                    """.formatted(space, item, value);
            error(send("POST", "/reservations", request, user), 400, "VALIDATION_ERROR");
        }
        assertThat(jdbc.queryForObject("select stock from equipment where id = ?", Integer.class, item)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from reservation where space_id = ?", Integer.class, space))
                .isZero();
    }

    @Test
    void availabilityFiltersDisciplineAndItemsThatCannotBeRequested() throws Exception {
        String admin = administrator();
        String user = completeLogin("equipment-filter@unimagdalena.edu.co");
        UUID available = createItem(admin, 1);
        UUID zero = createItem(admin, 0);
        UUID inactive = createItem(admin, 1);
        assertThat(send("PUT", "/admin/equipment/" + available, "{\"discipline\":\"TENNIS\"}", admin).statusCode())
                .isEqualTo(200);
        assertThat(send("PUT", "/admin/equipment/" + zero, "{\"discipline\":\"TENNIS\"}", admin).statusCode())
                .isEqualTo(200);
        assertThat(send("PUT", "/admin/equipment/" + inactive,
                "{\"discipline\":\"TENNIS\",\"status\":\"INACTIVE\"}", admin).statusCode()).isEqualTo(200);
        String path = "/equipment/availability?startAt=2026-11-11T13:00:00Z&endAt=2026-11-11T14:00:00Z"
                + "&discipline=TENNIS";
        var items = body(send("GET", path, null, user)).get("items");
        assertThat(items.valueStream().map(value -> value.get("equipmentId").asString()).toList())
                .contains(available.toString()).doesNotContain(zero.toString(), inactive.toString());
        assertThat(send("POST", "/reservations", reservation(space(), available, 1, "08:00"), user).statusCode())
                .isEqualTo(201);
        assertThat(body(send("GET", path, null, user)).get("items").valueStream()
                .noneMatch(value -> value.get("equipmentId").asString().equals(available.toString()))).isTrue();
    }

    @Test
    void availabilityUsesMaximumSimultaneousAndExcludesInactiveItems() throws Exception {
        String admin = administrator();
        String first = completeLogin("equipment-first@unimagdalena.edu.co");
        String second = completeLogin("equipment-second@unimagdalena.edu.co");
        UUID item = createItem(admin, 3);
        UUID firstSpace = space();
        UUID secondSpace = space();
        assertThat(send("POST", "/reservations", reservation(firstSpace, item, 2, "08:00"), first).statusCode())
                .isEqualTo(201);
        assertThat(send("POST", "/reservations", reservation(secondSpace, item, 2, "09:00"), second).statusCode())
                .isEqualTo(201);
        String path = "/equipment/availability?startAt=2026-11-11T13:00:00Z&endAt=2026-11-11T15:00:00Z";
        var response = send("GET", path, null, first);
        assertThat(response.statusCode()).isEqualTo(200);
        var found = body(response).get("items").valueStream()
                .filter(value -> value.get("equipmentId").asString().equals(item.toString())).findFirst().orElseThrow();
        assertThat(found.get("committed").asInt()).isEqualTo(2);
        assertThat(found.get("available").asInt()).isEqualTo(1);
        error(send("PUT", "/admin/equipment/" + item, "{\"stock\":1}", admin), 409, "STOCK_BELOW_COMMITTED");
        assertThat(send("PUT", "/admin/equipment/" + item, "{\"status\":\"INACTIVE\"}", admin).statusCode())
                .isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from reservation_equipment where equipment_id = ?",
                Integer.class, item)).isEqualTo(2);
        assertThat(body(send("GET", path, null, first)).get("items").valueStream()
                .noneMatch(value -> value.get("equipmentId").asString().equals(item.toString()))).isTrue();
    }

    @Test
    void activeReservationPreventsStockReductionAndCancellationReleasesIt() throws Exception {
        String admin = administrator();
        String user = completeLogin("equipment-active@unimagdalena.edu.co");
        String next = completeLogin("equipment-after-cancel@unimagdalena.edu.co");
        UUID item = createItem(admin, 2);
        UUID space = space();
        var created = send("POST", "/reservations", reservation(space, item, 2, "08:00"), user);
        assertThat(created.statusCode()).isEqualTo(201);
        String reservationId = body(created).get("id").asString();
        assertThat(send("POST", "/reservations/" + reservationId + "/cancellation", null, user).statusCode())
                .isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from reservation_equipment where reservation_id = ?",
                Integer.class, UUID.fromString(reservationId))).isEqualTo(1);
        assertThat(send("POST", "/reservations", reservation(space, item, 2, "08:00"), next).statusCode())
                .isEqualTo(201);
        clock.set(java.time.Instant.parse("2026-11-11T13:30:00Z"));
        error(send("PUT", "/admin/equipment/" + item, "{\"stock\":1}", admin), 409, "STOCK_BELOW_COMMITTED");
    }

    @Test
    void editingStockRacesWithReservationWithoutBreakingCommitments() throws Exception {
        String admin = administrator();
        String user = completeLogin("equipment-edit-race@unimagdalena.edu.co");
        UUID item = createItem(admin, 1);
        String request = reservation(space(), item, 1, "08:00");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var booking = executor.submit(() -> { start.await(); return send("POST", "/reservations", request, user); });
            var edit = executor.submit(() -> {
                start.await();
                return send("PUT", "/admin/equipment/" + item, "{\"stock\":0}", admin);
            });
            start.countDown();
            int bookingStatus = booking.get().statusCode();
            int editStatus = edit.get().statusCode();
            assertThat(bookingStatus == 201 && editStatus == 409 || bookingStatus == 409 && editStatus == 200).isTrue();
        }
        int stock = jdbc.queryForObject("select stock from equipment where id = ?", Integer.class, item);
        int committed = jdbc.queryForObject(
                "select coalesce(sum(quantity), 0) from reservation_equipment where equipment_id = ?",
                Integer.class, item);
        assertThat(stock).isGreaterThanOrEqualTo(committed);
    }

    @Test
    void receiptFromRealReservationContainsEachItemAndQuantity() throws Exception {
        String admin = administrator();
        String email = "equipment-receipt@unimagdalena.edu.co";
        String user = completeLogin(email);
        UUID first = createItem(admin, 2);
        UUID second = createItem(admin, 3);
        String firstName = jdbc.queryForObject("select name from equipment where id = ?", String.class, first);
        String secondName = jdbc.queryForObject("select name from equipment where id = ?", String.class, second);
        String request = """
                {"spaceId":"%s","startAt":"2026-11-11T08:00:00-05:00","slots":1,
                 "equipment":[{"equipmentId":"%s","quantity":2},{"equipmentId":"%s","quantity":3}]}
                """.formatted(space(), first, second);
        assertThat(send("POST", "/reservations", request, user).statusCode()).isEqualTo(201);
        assertThat(mail.pdfs).containsKey(email);
        var pdf = new PdfReader(mail.pdfs.get(email));
        try {
            assertThat(new PdfTextExtractor(pdf).getTextFromPage(1))
                    .contains(firstName + " - cantidad: 2", secondName + " - cantidad: 3");
        } finally {
            pdf.close();
        }
    }

    @Test
    void reservationRejectsInvalidEquipmentAndLeavesNoPartialRows() throws Exception {
        String admin = administrator();
        String user = completeLogin("equipment-invalid@unimagdalena.edu.co");
        UUID available = createItem(admin, 2);
        UUID inactive = createItem(admin, 1);
        assertThat(send("PUT", "/admin/equipment/" + inactive, "{\"status\":\"INACTIVE\"}", admin).statusCode())
                .isEqualTo(200);
        UUID space = space();
        String prefix = """
                {"spaceId":"%s","startAt":"2026-11-11T08:00:00-05:00","slots":1,"equipment":
                """.formatted(space);
        String item = "{\"equipmentId\":\"" + available + "\",\"quantity\":1}";
        error(send("POST", "/reservations", prefix + "[" + item + "," + item + "]}", user),
                400, "VALIDATION_ERROR");
        error(send("POST", "/reservations", prefix + "[{\"equipmentId\":\"" + available + "\",\"quantity\":0}]}", user),
                400, "INVALID_QUANTITY");
        error(send("POST", "/reservations", prefix + "[{\"equipmentId\":\"" + UUID.randomUUID()
                + "\",\"quantity\":1}]}", user), 404, "EQUIPMENT_NOT_FOUND");
        error(send("POST", "/reservations", prefix + "[" + item + ",{\"equipmentId\":\"" + inactive
                + "\",\"quantity\":1}]}", user), 409, "EQUIPMENT_UNAVAILABLE");
        assertThat(jdbc.queryForObject("select count(*) from reservation where space_id = ?", Integer.class, space))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from reservation_equipment where equipment_id in (?, ?)",
                Integer.class, available, inactive)).isZero();
    }

    @Test
    void omittedAndEmptyEquipmentCreateNoAssociations() throws Exception {
        String first = completeLogin("equipment-omitted@unimagdalena.edu.co");
        String second = completeLogin("equipment-empty@unimagdalena.edu.co");
        UUID firstSpace = space();
        UUID secondSpace = space();
        String request = "{\"spaceId\":\"%s\",\"startAt\":\"2026-11-11T08:00:00-05:00\",\"slots\":1%s}";
        var omitted = send("POST", "/reservations", request.formatted(firstSpace, ""), first);
        var empty = send("POST", "/reservations", request.formatted(secondSpace, ",\"equipment\":[]"), second);
        assertThat(omitted.statusCode()).isEqualTo(201);
        assertThat(empty.statusCode()).isEqualTo(201);
        for (var response : java.util.List.of(omitted, empty)) {
            UUID reservationId = UUID.fromString(body(response).get("id").asString());
            assertThat(jdbc.queryForObject("select count(*) from reservation_equipment where reservation_id = ?",
                    Integer.class, reservationId)).isZero();
        }
    }

    @Test
    void concurrentReservationsCannotConsumeTheLastItemTwice() throws Exception {
        String admin = administrator();
        String first = completeLogin("equipment-race-one@unimagdalena.edu.co");
        String second = completeLogin("equipment-race-two@unimagdalena.edu.co");
        UUID item = createItem(admin, 1);
        String firstRequest = reservation(space(), item, 1, "08:00");
        String secondRequest = reservation(space(), item, 1, "08:00");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return send("POST", "/reservations", firstRequest, first); });
            var b = executor.submit(() -> { start.await(); return send("POST", "/reservations", secondRequest, second); });
            start.countDown();
            assertThat(java.util.List.of(a.get().statusCode(), b.get().statusCode())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("select sum(quantity) from reservation_equipment where equipment_id = ?",
                Integer.class, item)).isEqualTo(1);
    }

    private UUID createItem(String admin, int stock) throws Exception {
        var response = send("POST", "/admin/equipment",
                "{\"name\":\"Item " + UUID.randomUUID() + "\",\"stock\":" + stock + "}", admin);
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(body(response).get("id").asString());
    }

    private String reservation(UUID space, UUID item, int quantity, String time) {
        return """
                {"spaceId":"%s","startAt":"2026-11-11T%s:00-05:00","slots":1,
                 "equipment":[{"equipmentId":"%s","quantity":%d}]}
                """.formatted(space, time, item, quantity);
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
