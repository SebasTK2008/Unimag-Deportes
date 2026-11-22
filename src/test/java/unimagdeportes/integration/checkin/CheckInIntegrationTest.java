package unimagdeportes.integration.checkin;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import unimagdeportes.support.AuthTestSupport;

@TestPropertySource(properties = "app.checkin.frontend-origin=https://client.example")
class CheckInIntegrationTest extends AuthTestSupport {
    @Test
    void recordsCheckInAndRepeatsWithoutChangingTimeOrAudit() throws Exception {
        String user = completeLogin("usuario@unimagdalena.edu.co");
        UUID userId = UUID.fromString(decoder.decode(user).getSubject());
        UUID space = space();
        UUID reservation = reservation(space, userId, "2026-11-11T12:10:00Z", "2026-11-11T13:10:00Z");
        clock.set(Instant.parse("2026-11-11T12:00:00.123456789Z"));
        var first = send("POST", "/checkin/" + space, null, user);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(body(first).get("status").asString()).isEqualTo("IN_USE");
        assertThat(body(first).get("checkedInAt").asString()).endsWith("-05:00");
        clock.advance(java.time.Duration.ofMinutes(1));
        var repeated = send("POST", "/checkin/" + space, null, user);
        assertThat(repeated.body()).isEqualTo(first.body());
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_id = ? and action = 'CHECK_IN'",
                Integer.class, reservation)).isEqualTo(1);
    }

    @Test
    void repeatsExistingCheckInAtAndAfterEndWithoutAnotherAudit() throws Exception {
        String user = completeLogin("repeat-ended@unimagdalena.edu.co");
        UUID userId = UUID.fromString(decoder.decode(user).getSubject());
        UUID space = space();
        UUID id = reservation(space, userId, "2026-11-11T12:00:00Z", "2026-11-11T13:00:00Z");
        var first = send("POST", "/checkin/" + space, null, user);
        assertThat(first.statusCode()).isEqualTo(200);
        for (Instant now : new Instant[]{Instant.parse("2026-11-11T13:00:00Z"), Instant.parse("2026-11-11T13:00:01Z")}) {
            clock.set(now);
            assertThat(send("POST", "/checkin/" + space, null, user).body()).isEqualTo(first.body());
        }
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_id=? and action='CHECK_IN'",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void previousOccupantStillPreventsCheckInInsideOwnEarlyWindow() throws Exception {
        String user = completeLogin("next@unimagdalena.edu.co");
        String other = completeLogin("previous@unimagdalena.edu.co");
        UUID space = space();
        reservation(space, UUID.fromString(decoder.decode(other).getSubject()),
                "2026-11-11T11:00:00Z", "2026-11-11T12:05:00Z");
        reservation(space, UUID.fromString(decoder.decode(user).getSubject()),
                "2026-11-11T12:05:00Z", "2026-11-11T13:05:00Z");
        error(send("POST", "/checkin/" + space, null, user), 403, "FORBIDDEN");
        clock.set(Instant.parse("2026-11-11T12:05:00Z"));
        assertThat(send("POST", "/checkin/" + space, null, user).statusCode()).isEqualTo(200);
    }

    @Test
    void realReservationCreatedWithinToleranceCanCheckIn() throws Exception {
        String user = completeLogin("tolerance-checkin@unimagdalena.edu.co");
        UUID space = space();
        jdbc.update("insert into space_discipline (space_id,discipline) values (?, 'TENNIS')", space);
        clock.set(Instant.parse("2026-11-11T12:05:00Z"));
        var created = send("POST", "/reservations", """
                {"spaceId":"%s","startAt":"2026-11-11T07:00:00-05:00","slots":1}
                """.formatted(space), user);
        assertThat(created.statusCode()).isEqualTo(201);
        var response = send("POST", "/checkin/" + space, null, user);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body(response).get("reservationId")).isEqualTo(body(created).get("id"));
    }

    @Test
    void rejectsEarlyClosedCancelledMissingAndUnauthenticated() throws Exception {
        String user = completeLogin("usuario@unimagdalena.edu.co");
        UUID userId = UUID.fromString(decoder.decode(user).getSubject());
        UUID space = space();
        error(send("POST", "/checkin/" + space, null, user), 404, "NO_ACTIVE_RESERVATION");
        assertThat(send("POST", "/checkin/" + space, null, null).statusCode()).isEqualTo(401);
        UUID id = reservation(space, userId, "2026-11-11T12:20:00Z", "2026-11-11T13:20:00Z");
        error(send("POST", "/checkin/" + space, null, user), 409, "CHECKIN_TOO_EARLY");
        clock.set(Instant.parse("2026-11-11T13:20:00Z"));
        error(send("POST", "/checkin/" + space, null, user), 409, "CHECKIN_WINDOW_CLOSED");
        jdbc.update("update reservation set status = 'CANCELLED_ON_TIME' where id = ?", id);
        error(send("POST", "/checkin/" + space, null, user), 409, "RESERVATION_CANCELLED");
    }

    @Test
    void prioritizesAnotherUsersInProgressReservationOverOwnFutureCancelledOrMissing() throws Exception {
        String user = completeLogin("usuario@unimagdalena.edu.co");
        String other = completeLogin("otra@unimagdalena.edu.co");
        UUID userId = UUID.fromString(decoder.decode(user).getSubject());
        UUID otherId = UUID.fromString(decoder.decode(other).getSubject());
        for (String ownStatus : new String[]{"CONFIRMED", "CANCELLED_ON_TIME", "NONE"}) {
            UUID space = space();
            Instant now = clock.instant();
            reservation(space, otherId, now.minusSeconds(1800).toString(), now.plusSeconds(1800).toString());
            if (!ownStatus.equals("NONE")) {
                UUID own = reservation(space, userId, now.plusSeconds(3600).toString(),
                        now.plusSeconds(7200).toString());
                jdbc.update("update reservation set status = ? where id = ?", ownStatus, own);
            }
            error(send("POST", "/checkin/" + space, null, user), 403, "FORBIDDEN");
            clock.advance(java.time.Duration.ofHours(3));
        }
    }

    @Test
    void selectsOwnInProgressReservationBeforeFutureReservation() throws Exception {
        String user = completeLogin("usuario@unimagdalena.edu.co");
        UUID userId = UUID.fromString(decoder.decode(user).getSubject());
        UUID space = space();
        UUID current = reservation(space, userId, "2026-11-11T11:30:00Z", "2026-11-11T12:30:00Z");
        reservation(space, userId, "2026-11-11T13:00:00Z", "2026-11-11T14:00:00Z");
        var response = send("POST", "/checkin/" + space, null, user);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body(response).get("reservationId").asString()).isEqualTo(current.toString());
    }

    @Test
    void qrIsAdminOnlyAndContainsOnlyTheStableSpaceUrl() throws Exception {
        String admin = completeLogin("admin@unimagdalena.edu.co");
        String user = completeLogin("usuario@unimagdalena.edu.co");
        UUID space = space();
        error(send("GET", "/admin/spaces/" + space + "/qr", null, user), 403, "FORBIDDEN");
        error(send("GET", "/admin/spaces/" + UUID.randomUUID() + "/qr", null, admin), 404, "SPACE_NOT_FOUND");
        var response = send("GET", "/admin/spaces/" + space + "/qr", null, admin);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("image/svg+xml");
        assertThat(decode(response.body())).isEqualTo("https://client.example/checkin/" + space);
        assertThat(send("GET", "/admin/spaces/" + space + "/qr", null, admin).body()).isEqualTo(response.body());
    }

    private String decode(String svg) throws Exception {
        var size = Pattern.compile("viewBox=\"0 0 (\\d+) (\\d+)\"").matcher(svg);
        assertThat(size.find()).isTrue();
        int width = Integer.parseInt(size.group(1));
        int height = Integer.parseInt(size.group(2));
        int scale = 4;
        int[] pixels = new int[width * scale * height * scale];
        Arrays.fill(pixels, 0xffffffff);
        var cells = Pattern.compile("M(\\d+) (\\d+)h1v1h-1z").matcher(svg);
        while (cells.find()) {
            int x = Integer.parseInt(cells.group(1)) * scale;
            int y = Integer.parseInt(cells.group(2)) * scale;
            for (int row = 0; row < scale; row++) {
                Arrays.fill(pixels, (y + row) * width * scale + x,
                        (y + row) * width * scale + x + scale, 0xff000000);
            }
        }
        return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(width * scale, height * scale, pixels)))).getText();
    }

    private UUID space() {
        UUID id = UUID.randomUUID(), preview = UUID.randomUUID(), panorama = UUID.randomUUID();
        jdbc.update("insert into space_image (id,content_type,storage_key,size_bytes) "
                + "values (?, 'image/png', ?, 1), (?, 'image/png', ?, 1)",
                preview, "fixtures/" + preview, panorama, "fixtures/" + panorama);
        jdbc.update("insert into space (id, name, capacity, opening_time, closing_time, status, preview_image_id, panorama_image_id) "
                + "values (?, 'Cancha', 10, '06:00', '22:00', 'ACTIVE', ?, ?)", id, preview, panorama);
        return id;
    }

    private UUID reservation(UUID space, UUID user, String start, String end) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into reservation (id,user_id,space_id,start_at,end_at,status,created_at,"
                + "cancellation_threshold_minutes,checkin_opening_minutes) "
                + "values (?, ?, ?, ?::timestamptz, ?::timestamptz, 'CONFIRMED', now(), 60, 10)",
                id, user, space, start, end);
        return id;
    }
}
