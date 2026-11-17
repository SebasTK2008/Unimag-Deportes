package unimagdeportes.integration.availability;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import unimagdeportes.dto.block.BlockDtos.CreateBlock;
import unimagdeportes.model.block.BlockType;
import unimagdeportes.service.block.BlockService;
import unimagdeportes.support.RulesTestSupport;

class AvailabilityIntegrationTest extends RulesTestSupport {
    @Autowired BlockService blocks;

    @Test
    void calendarAndListEnforceContractsAndSharedSpaceOccupancy() throws Exception {
        String user = completeLogin("calendar@unimagdalena.edu.co");
        UUID userId = UUID.fromString(decoder.decode(user).getSubject());
        UUID active = space("ACTIVE");
        UUID maintenance = space("MAINTENANCE");
        jdbc.update("insert into space_discipline (space_id, discipline) values (?, 'TENNIS'), (?, 'VOLLEYBALL'), "
                + "(?, 'TENNIS')", active, active, maintenance);
        jdbc.update("insert into reservation (id,user_id,space_id,start_at,end_at,status,created_at,"
                        + "cancellation_threshold_minutes,checkin_opening_minutes) "
                        + "values (?, ?, ?, '2026-11-11T13:00:00Z', '2026-11-11T14:00:00Z', "
                        + "'CONFIRMED', now(), 60, 10)", UUID.randomUUID(), userId, active);
        jdbc.update("insert into institutional_block (id,space_id,type,reason,start_at,end_at) "
                + "values (?, ?, 'CLASS', 'Clase', '2026-11-11T14:00:00Z', '2026-11-11T15:00:00Z')",
                UUID.randomUUID(), active);

        var list = body(send("GET", "/spaces?discipline=TENNIS", null, user)).get("spaces");
        assertThat(list.toString()).contains(active.toString()).doesNotContain(maintenance.toString());
        for (String discipline : new String[]{"TENNIS", "VOLLEYBALL"}) {
            var result = body(send("GET", "/spaces/" + active + "/availability?date=2026-11-11&discipline="
                    + discipline, null, user));
            var slots = result.get("slots");
            assertThat(slots.get(0).get("status").asString()).isEqualTo("FREE");
            assertThat(slots.get(1).get("status").asString()).isEqualTo("OCCUPIED");
            assertThat(slots.get(2).get("status").asString()).isEqualTo("BLOCKED");
            assertThat(slots.get(2).get("reason").asString()).isEqualTo("Clase");
            assertThat(slots.get(2).get("bookable").asBoolean()).isFalse();
        }
        var maintained = body(send("GET", "/spaces/" + maintenance + "/availability?date=2026-11-11", null, user));
        assertThat(maintained.get("slots").get(0).get("bookable").asBoolean()).isFalse();
        jdbc.update("update app_user set suspended_until = '2026-12-01T00:00:00Z' where id = ?", userId);
        assertThat(send("GET", "/spaces", null, user).statusCode()).isEqualTo(200);
    }

    @Test
    void validatesSessionDateHorizonAndDiscipline() throws Exception {
        String user = completeLogin("calendar-errors@unimagdalena.edu.co");
        UUID id = space("ACTIVE");
        String path = "/spaces/" + id + "/availability";
        error(send("GET", "/spaces", null, null), 401, "TOKEN_INVALID");
        error(send("GET", path + "?date=2026-11-11", null, null), 401, "TOKEN_INVALID");
        error(send("GET", path + "/stream", null, null), 401, "TOKEN_INVALID");
        error(send("GET", "/spaces?discipline=INVALID", null, user), 400, "VALIDATION_ERROR");
        error(send("GET", path + "?date=2026-02-30", null, user), 400, "INVALID_DATE");
        var outside = send("GET", path + "?date=2026-11-18", null, user);
        error(outside, 400, "OUTSIDE_CALENDAR_HORIZON");
        assertThat(body(outside).get("details").get("maxDate").asString()).isEqualTo("2026-11-17");
        assertThat(send("GET", path + "?date=2026-11-17", null, user).statusCode()).isEqualTo(200);
        error(send("GET", "/spaces/" + UUID.randomUUID() + "/availability?date=2026-11-11", null, user),
                404, "SPACE_NOT_FOUND");
    }

    @Test
    @Timeout(15)
    void streamReceivesCommittedBlockEventWithBearerAuthorization() throws Exception {
        String admin = administrator();
        UUID adminId = UUID.fromString(decoder.decode(admin).getSubject());
        UUID id = space("ACTIVE");
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/spaces/" + id
                        + "/availability/stream"))
                .header("Authorization", "Bearer " + admin).header("Accept", "text/event-stream").GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/event-stream");
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(response.body()))) {
            assertThat(reader.readLine()).startsWith(":");
            blocks.create(new CreateBlock(id, BlockType.CLASS, "Clase", Instant.parse("2026-11-11T15:00:00Z"),
                    Instant.parse("2026-11-11T16:00:00Z"), null), adminId);
            assertThat(reader.lines().filter(line -> line.startsWith("event:")).findFirst().orElseThrow())
                    .isEqualTo("event:availability-changed");
            assertThat(reader.readLine()).contains(id.toString()).contains("changedAt").contains("-05:00");
        }
    }

    private UUID space(String status) {
        UUID id = UUID.randomUUID();
        UUID preview = UUID.randomUUID();
        UUID panorama = UUID.randomUUID();
        jdbc.update("insert into space_image (id, content_type, content) values (?, 'image/png', ?), (?, 'image/png', ?)",
                preview, new byte[]{1}, panorama, new byte[]{1});
        jdbc.update("insert into space (id,name,capacity,opening_time,closing_time,status,preview_image_id,panorama_image_id)"
                + " values (?, 'Cancha', 10, '07:00', '10:00', ?, ?, ?)", id, status, preview, panorama);
        return id;
    }
}
