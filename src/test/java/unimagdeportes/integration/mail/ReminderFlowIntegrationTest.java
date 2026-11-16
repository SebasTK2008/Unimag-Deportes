package unimagdeportes.integration.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import unimagdeportes.scheduler.ReminderJob;
import unimagdeportes.support.ReservationTestSupport;

class ReminderFlowIntegrationTest extends ReservationTestSupport {
    @Autowired ReminderJob job;

    @Test
    void reservationCreatedThroughTheApiReceivesTheReminderWithTheCancellationLink() throws Exception {
        String user = completeLogin("reminder-flow@unimagdalena.edu.co");
        UUID reservation = reserveAtTen(user);
        clock.advance(Duration.ofHours(1));
        mail.bodies.clear();

        job.sendReminders();

        assertThat(mail.bodies.get("reminder-flow@unimagdalena.edu.co")).contains(
                "11/11/2026 10:00", "https://client.example/reservations/" + reservation + "/cancel",
                "https://client.example/contact");
    }

    @Test
    void cancelledReservationDoesNotReceiveTheReminder() throws Exception {
        String user = completeLogin("reminder-cancelled@unimagdalena.edu.co");
        UUID reservation = reserveAtTen(user);
        assertThat(send("POST", "/reservations/" + reservation + "/cancellation", null, user).statusCode())
                .isEqualTo(200);
        clock.advance(Duration.ofHours(1));
        mail.bodies.clear();

        job.sendReminders();

        assertThat(mail.bodies).doesNotContainKey("reminder-cancelled@unimagdalena.edu.co");
    }

    /** Se crea a las 07:00 para las 10:00 (Bogotá): tres horas de anticipación, elegible para el recordatorio. */
    private UUID reserveAtTen(String user) throws Exception {
        var created = send("POST", "/reservations",
                "{\"spaceId\":\"%s\",\"startAt\":\"2026-11-11T10:00:00-05:00\",\"slots\":1}".formatted(space()), user);
        assertThat(created.statusCode()).isEqualTo(201);
        return UUID.fromString(body(created).get("id").asString());
    }
}
