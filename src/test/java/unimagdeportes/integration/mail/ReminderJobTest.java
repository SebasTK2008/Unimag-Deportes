package unimagdeportes.integration.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import unimagdeportes.scheduler.ReminderJob;
import unimagdeportes.support.AuthTestSupport;

class ReminderJobTest extends AuthTestSupport {
    @Autowired ReminderJob job;

    @Test
    void sendsOnceAtTwoHourBoundaryAndOmitsLateOrCancelledReservations() {
        UUID eligible = reservation("eligible", START.plus(Duration.ofHours(2)), START, "CONFIRMED");
        UUID late = reservation("late", START.plus(Duration.ofHours(2)), START.plusSeconds(1), "CONFIRMED");
        UUID cancelled = reservation("cancelled", START.plus(Duration.ofHours(2)), START, "CANCELLED_ON_TIME");
        job.sendReminders();
        assertThat(mail.bodies).containsKey("eligible@unimagdalena.edu.co")
                .doesNotContainKeys("late@unimagdalena.edu.co", "cancelled@unimagdalena.edu.co");
        assertThat(sentAt(eligible)).isNotNull();
        assertThat(sentAt(late)).isNull();
        assertThat(sentAt(cancelled)).isNull();
        mail.bodies.clear();
        job.sendReminders();
        assertThat(mail.bodies).isEmpty();
    }

    @Test
    void failedDeliveryDoesNotMarkReminderAndCanRetry() {
        UUID id = reservation("retry", START.plus(Duration.ofHours(2)), START, "CONFIRMED");
        mail.failing = true;
        job.sendReminders();
        assertThat(sentAt(id)).isNull();
        mail.failing = false;
        job.sendReminders();
        assertThat(sentAt(id)).isNotNull();
    }

    @Test
    void waitsUntilTwoHoursBeforeStart() {
        UUID id = reservation("boundary", START.plus(Duration.ofHours(2)).plusSeconds(1), START, "CONFIRMED");
        job.sendReminders();
        assertThat(sentAt(id)).isNull();
        clock.advance(Duration.ofSeconds(1));
        job.sendReminders();
        assertThat(sentAt(id)).isNotNull();
    }

    @Test
    void concurrentExecutionsInOneProcessSendOnlyOnce() throws Exception {
        UUID id = reservation("concurrent", START.plus(Duration.ofHours(2)), START, "CONFIRMED");
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = List.of(executor.submit(() -> { barrier.await(); job.sendReminders(); return null; }),
                    executor.submit(() -> { barrier.await(); job.sendReminders(); return null; }));
            for (var task : tasks) task.get();
        }
        assertThat(sentAt(id)).isNotNull();
        assertThat(mail.sentCount.get()).isEqualTo(1);
    }

    private UUID reservation(String name, Instant start, Instant created, String status) {
        UUID id = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID space = UUID.randomUUID();
        jdbc.update("insert into app_user (id, email, name, role, created_at) values (?, ?, ?, 'USER', ?)",
                user, name + "@unimagdalena.edu.co", name, Timestamp.from(START));
        jdbc.update("insert into space (id, name, capacity, opening_time, closing_time, status) "
                + "values (?, ?, 1, '06:00', '22:00', 'ACTIVE')", space, name);
        jdbc.update("insert into reservation (id, user_id, space_id, start_at, end_at, status, created_at, "
                        + "cancellation_threshold_minutes, checkin_opening_minutes) values (?, ?, ?, ?, ?, ?, ?, 60, 10)",
                id, user, space, Timestamp.from(start), Timestamp.from(start.plusSeconds(3600)), status,
                Timestamp.from(created));
        return id;
    }

    private Timestamp sentAt(UUID id) {
        return jdbc.queryForObject("select reminder_sent_at from reservation where id = ?", Timestamp.class, id);
    }
}
