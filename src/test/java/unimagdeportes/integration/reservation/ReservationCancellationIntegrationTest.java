package unimagdeportes.integration.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import unimagdeportes.service.quota.QuotaService;
import unimagdeportes.support.ReservationTestSupport;

class ReservationCancellationIntegrationTest extends ReservationTestSupport {
    @Autowired QuotaService quota;

    @Test
    void onTimeCancellationFreesSpaceReturnsHoursAndNotifies() throws Exception {
        String user = completeLogin("cancel-on-time@unimagdalena.edu.co");
        UUID reservation = reserve(user);

        var cancelled = send("POST", "/reservations/" + reservation + "/cancellation", null, user);
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(body(cancelled).get("status").asString()).isEqualTo("CANCELLED_ON_TIME");
        assertThat(body(cancelled).get("hoursCounted").asInt()).isZero();
        assertThat(statusOf(reservation)).isEqualTo("CANCELLED_ON_TIME");
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_log where action = 'RESERVATION_CANCELLED' and entity_id = ?",
                Integer.class, reservation)).isEqualTo(1);
        assertThat(mail.bodies.get("cancel-on-time@unimagdalena.edu.co")).contains("cancelada");
        assertThat(usedHours(user)).isZero();
    }

    @Test
    void lateCancellationFreesSpaceButKeepsHours() throws Exception {
        String user = completeLogin("cancel-late@unimagdalena.edu.co");
        UUID space = space();
        UUID reservation = reserve(user, space);
        clock.advance(Duration.ofMinutes(30));

        var cancelled = send("POST", "/reservations/" + reservation + "/cancellation", null, user);
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(body(cancelled).get("status").asString()).isEqualTo("CANCELLED_LATE");
        assertThat(body(cancelled).get("hoursCounted").asInt()).isEqualTo(1);
        assertThat(statusOf(reservation)).isEqualTo("CANCELLED_LATE");
        assertThat(usedHours(user)).isEqualTo(1);

        String other = completeLogin("cancel-late-other@unimagdalena.edu.co");
        reserve(other, space);
    }

    @Test
    void cancellationAfterTheSlotStartedIsLate() throws Exception {
        String user = completeLogin("cancel-started@unimagdalena.edu.co");
        UUID reservation = reserve(user);
        clock.advance(Duration.ofMinutes(65));

        var cancelled = send("POST", "/reservations/" + reservation + "/cancellation", null, user);
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(body(cancelled).get("status").asString()).isEqualTo("CANCELLED_LATE");
        assertThat(usedHours(user)).isEqualTo(1);
    }

    @Test
    void reservationWhoseSlotEndedCannotBeCancelled() throws Exception {
        String user = completeLogin("cancel-ended@unimagdalena.edu.co");
        UUID reservation = reserve(user);
        clock.advance(Duration.ofHours(2));

        error(send("GET", "/reservations/" + reservation + "/cancellation-preview", null, user), 409,
                "RESERVATION_NOT_CANCELLABLE");
        error(send("POST", "/reservations/" + reservation + "/cancellation", null, user), 409,
                "RESERVATION_NOT_CANCELLABLE");
        assertThat(statusOf(reservation)).isEqualTo("CONFIRMED");
    }

    @Test
    void previewDoesNotChangeTheReservation() throws Exception {
        String user = completeLogin("preview-readonly@unimagdalena.edu.co");
        UUID reservation = reserve(user);

        assertThat(send("GET", "/reservations/" + reservation + "/cancellation-preview", null, user).statusCode())
                .isEqualTo(200);
        assertThat(statusOf(reservation)).isEqualTo("CONFIRMED");
        assertThat(usedHours(user)).isEqualTo(1);
    }

    @Test
    void alreadyCancelledReservationCannotBeCancelledOrPreviewedAgain() throws Exception {
        String user = completeLogin("cancel-twice@unimagdalena.edu.co");
        UUID reservation = reserve(user);
        assertThat(send("POST", "/reservations/" + reservation + "/cancellation", null, user).statusCode())
                .isEqualTo(200);

        error(send("POST", "/reservations/" + reservation + "/cancellation", null, user), 409,
                "RESERVATION_NOT_CANCELLABLE");
        error(send("GET", "/reservations/" + reservation + "/cancellation-preview", null, user), 409,
                "RESERVATION_NOT_CANCELLABLE");
        assertThat(statusOf(reservation)).isEqualTo("CANCELLED_ON_TIME");
    }

    @Test
    void checkedInReservationCannotBeCancelled() throws Exception {
        String user = completeLogin("cancel-checked-in@unimagdalena.edu.co");
        UUID space = space();
        UUID reservation = reserve(user, space);
        clock.advance(Duration.ofHours(1));
        assertThat(send("POST", "/checkin/" + space, null, user).statusCode()).isEqualTo(200);

        error(send("POST", "/reservations/" + reservation + "/cancellation", null, user), 409,
                "RESERVATION_NOT_CANCELLABLE");
        error(send("GET", "/reservations/" + reservation + "/cancellation-preview", null, user), 409,
                "RESERVATION_NOT_CANCELLABLE");
        assertThat(statusOf(reservation)).isEqualTo("IN_USE");
    }

    @Test
    void cancelledReservationKeepsEquipmentHistoryAndReleasesStock() throws Exception {
        String user = completeLogin("cancel-stock@unimagdalena.edu.co");
        UUID space = space();
        var item = equipment("Balón de fútbol", 1);
        UUID reservation = reserveWithEquipment(user, space, item.id());
        assertThat(equipmentRows(reservation)).isEqualTo(1);

        var cancelled = send("POST", "/reservations/" + reservation + "/cancellation", null, user);
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(body(cancelled).get("equipment").get(0).get("name").asString()).isEqualTo(item.name());
        assertThat(equipmentRows(reservation)).isEqualTo(1);

        String other = completeLogin("cancel-stock-other@unimagdalena.edu.co");
        reserveWithEquipment(other, space, item.id());
    }

    @Test
    void mailFailureDoesNotRollbackCancellation() throws Exception {
        String user = completeLogin("cancel-mail-failure@unimagdalena.edu.co");
        UUID reservation = reserve(user);
        mail.failing = true;

        var cancelled = send("POST", "/reservations/" + reservation + "/cancellation", null, user);
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(statusOf(reservation)).isEqualTo("CANCELLED_ON_TIME");
    }

    private UUID reserve(String user) throws Exception {
        return reserve(user, space());
    }

    private UUID reserve(String user, UUID space) throws Exception {
        var created = send("POST", "/reservations",
                "{\"spaceId\":\"%s\",\"startAt\":\"2026-11-11T08:00:00-05:00\",\"slots\":1}".formatted(space), user);
        assertThat(created.statusCode()).isEqualTo(201);
        return UUID.fromString(body(created).get("id").asString());
    }

    private UUID reserveWithEquipment(String user, UUID space, UUID item) throws Exception {
        var created = send("POST", "/reservations", """
                {"spaceId":"%s","startAt":"2026-11-11T08:00:00-05:00","slots":1,
                 "equipment":[{"equipmentId":"%s","quantity":1}]}
                """.formatted(space, item), user);
        assertThat(created.statusCode()).isEqualTo(201);
        return UUID.fromString(body(created).get("id").asString());
    }

    /** Horas usadas en la semana del 9 al 15 de noviembre de 2026 (lunes a domingo, America/Bogota). */
    private long usedHours(String token) {
        UUID userId = UUID.fromString(decoder.decode(token).getSubject());
        Instant weekStart = Instant.parse("2026-11-09T05:00:00Z");
        return quota.usedInWeek(userId, weekStart, weekStart.plus(Duration.ofDays(7))).toHours();
    }

    private String statusOf(UUID reservation) {
        return jdbc.queryForObject("select status from reservation where id = ?", String.class, reservation);
    }

    private int equipmentRows(UUID reservation) {
        return jdbc.queryForObject("select count(*) from reservation_equipment where reservation_id = ?",
                Integer.class, reservation);
    }
}
