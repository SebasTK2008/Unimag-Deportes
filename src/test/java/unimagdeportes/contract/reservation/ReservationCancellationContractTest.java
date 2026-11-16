package unimagdeportes.contract.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import unimagdeportes.support.ReservationTestSupport;

class ReservationCancellationContractTest extends ReservationTestSupport {

    @Test
    void previewReportsOnTimeCancellation() throws Exception {
        String user = completeLogin("preview-on-time@unimagdalena.edu.co");
        UUID reservation = reserve(user);
        var preview = send("GET", "/reservations/" + reservation + "/cancellation-preview", null, user);
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(body(preview).get("reservationId").asString()).isEqualTo(reservation.toString());
        assertThat(body(preview).get("late").asBoolean()).isFalse();
        assertThat(body(preview).get("hoursCounted").asInt()).isZero();
        assertThat(body(preview).get("message").asString()).contains("a tiempo");
    }

    @Test
    void previewReportsLateCancellation() throws Exception {
        String user = completeLogin("preview-late@unimagdalena.edu.co");
        UUID reservation = reserve(user);
        clock.advance(Duration.ofMinutes(30));
        var preview = send("GET", "/reservations/" + reservation + "/cancellation-preview", null, user);
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(body(preview).get("late").asBoolean()).isTrue();
        assertThat(body(preview).get("hoursCounted").asInt()).isEqualTo(1);
        assertThat(body(preview).get("message").asString()).contains("tardía");
    }

    @Test
    void cancellationReturnsTheExpectedStatusAndFields() throws Exception {
        String user = completeLogin("cancel-status@unimagdalena.edu.co");
        UUID reservation = reserve(user);
        var cancelled = send("POST", "/reservations/" + reservation + "/cancellation", null, user);
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(body(cancelled).get("id").asString()).isEqualTo(reservation.toString());
        assertThat(body(cancelled).get("status").asString()).isEqualTo("CANCELLED_ON_TIME");
        assertThat(body(cancelled).get("hoursCounted").asInt()).isZero();
        assertThat(body(cancelled).get("cancelledAt").asString()).isEqualTo("2026-11-11T07:00:00-05:00");
        assertThat(body(cancelled).get("equipment").isArray()).isTrue();
    }

    @Test
    void rejectsForeignAndNotFoundReservations() throws Exception {
        String owner = completeLogin("cancel-owner@unimagdalena.edu.co");
        String other = completeLogin("cancel-other@unimagdalena.edu.co");
        UUID reservation = reserve(owner);
        error(send("POST", "/reservations/" + reservation + "/cancellation", null, other), 403, "FORBIDDEN");
        error(send("GET", "/reservations/" + reservation + "/cancellation-preview", null, other), 403, "FORBIDDEN");
        error(send("POST", "/reservations/" + UUID.randomUUID() + "/cancellation", null, owner), 404,
                "RESERVATION_NOT_FOUND");
        error(send("GET", "/reservations/" + UUID.randomUUID() + "/cancellation-preview", null, owner), 404,
                "RESERVATION_NOT_FOUND");
    }

    private UUID reserve(String user) throws Exception {
        UUID space = space();
        var created = send("POST", "/reservations",
                "{\"spaceId\":\"%s\",\"startAt\":\"2026-11-11T08:00:00-05:00\",\"slots\":1}".formatted(space), user);
        assertThat(created.statusCode()).isEqualTo(201);
        return UUID.fromString(body(created).get("id").asString());
    }
}
