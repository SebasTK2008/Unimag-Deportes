package unimagdeportes.integration.sanctions;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import unimagdeportes.event.AvailabilityChangedEvent;
import unimagdeportes.scheduler.ReservationLifecycleJob;
import unimagdeportes.service.quota.QuotaService;
import unimagdeportes.service.sanction.SanctionService;
import unimagdeportes.support.ReservationTestSupport;

@RecordApplicationEvents
class ReservationLifecycleIntegrationTest extends ReservationTestSupport {
    private static final Instant WEEK_START = Instant.parse("2026-11-09T05:00:00Z");
    @Autowired ReservationLifecycleJob job;
    @Autowired SanctionService sanctions;
    @Autowired QuotaService quota;
    @Autowired ApplicationEvents events;

    @Test
    void marksNoShowWhenTheWindowClosesAndKeepsTheHours() {
        UUID space = space();
        UUID absent = user("absent");
        UUID pending = user("pending");
        UUID closed = reservation(absent, space, "CONFIRMED", START.minus(Duration.ofHours(1)), START);
        UUID open = reservation(pending, space(), "CONFIRMED", START.minus(Duration.ofHours(1)).plusSeconds(1),
                START.plusSeconds(1));

        job.closeFinishedReservations();

        assertThat(statusOf(closed)).isEqualTo("NO_SHOW");
        assertThat(statusOf(open)).isEqualTo("CONFIRMED");
        assertThat(audits("NO_SHOW_REGISTERED")).isEqualTo(1);
        assertThat(mail.bodies.get("absent@unimagdalena.edu.co"))
                .contains("inasistencia", "11/11/2026 06:00", "https://client.example/contact");
        assertThat(quota.usedInWeek(absent, WEEK_START, WEEK_START.plus(Duration.ofDays(7))).toHours()).isEqualTo(1);

        job.closeFinishedReservations();
        assertThat(mail.sentCount.get()).isEqualTo(1);
        assertThat(audits("NO_SHOW_REGISTERED")).isEqualTo(1);
    }

    @Test
    void completesReservationsInUseAndAppliesTheSameWindowToToleranceBookings() {
        UUID inUse = reservation(user("in-use"), space(), "IN_USE", START.minus(Duration.ofHours(1)), START,
                START.minus(Duration.ofHours(1)), START.minus(Duration.ofMinutes(50)));
        Instant toleranceStart = START.minus(Duration.ofHours(1));
        UUID tolerance = reservation(user("tolerance"), space(), "CONFIRMED", toleranceStart, START,
                toleranceStart.plus(Duration.ofMinutes(5)), null);

        job.closeFinishedReservations();

        assertThat(statusOf(inUse)).isEqualTo("COMPLETED");
        assertThat(statusOf(tolerance)).isEqualTo("NO_SHOW");
        assertThat(mail.bodies).doesNotContainKey("in-use@unimagdalena.edu.co");
    }

    @Test
    void suspendsOnTheThresholdAndCancelsOnlyContainedFutureReservations() throws Exception {
        String token = completeLogin("suspended@unimagdalena.edu.co");
        UUID userId = UUID.fromString(decoder.decode(token).getSubject());
        UUID space = space();
        reservation(userId, space, "NO_SHOW", Instant.parse("2026-11-02T13:00:00Z"), Instant.parse("2026-11-02T14:00:00Z"));
        reservation(userId, space, "NO_SHOW", Instant.parse("2026-11-03T13:00:00Z"), Instant.parse("2026-11-03T14:00:00Z"));
        reservation(userId, space, "CONFIRMED", START.minus(Duration.ofHours(1)), START);
        Instant until = START.plus(Duration.ofDays(7));
        UUID contained = reservation(userId, space, "CONFIRMED", START.plus(Duration.ofDays(1)),
                START.plus(Duration.ofDays(1)).plus(Duration.ofHours(1)));
        UUID partial = reservation(userId, space, "CONFIRMED", until.minus(Duration.ofMinutes(30)),
                until.plus(Duration.ofMinutes(30)));
        UUID outside = reservation(userId, space, "CONFIRMED", START.plus(Duration.ofDays(8)),
                START.plus(Duration.ofDays(8)).plus(Duration.ofHours(1)));
        assertThat(usedHours(userId)).isEqualTo(2);
        mail.sentCount.set(0);

        job.closeFinishedReservations();

        assertThat(jdbc.queryForObject("select suspended_until from app_user where id = ?", Timestamp.class, userId)
                .toInstant()).isEqualTo(until);
        assertThat(jdbc.queryForObject("select count(*) from monthly_suspension where user_id = ? "
                + "and absence_month = date '2026-11-01'", Integer.class, userId)).isEqualTo(1);
        assertThat(audits("USER_SUSPENDED")).isEqualTo(1);
        assertThat(statusOf(contained)).isEqualTo("CANCELLED_BY_SUSPENSION");
        assertThat(statusOf(partial)).isEqualTo("CONFIRMED");
        assertThat(statusOf(outside)).isEqualTo("CONFIRMED");
        assertThat(audits("RESERVATION_CANCELLED_BY_SUSPENSION")).isEqualTo(1);
        assertThat(usedHours(userId)).isEqualTo(1);
        assertThat(mail.sentCount.get()).isEqualTo(3);

        error(send("POST", "/reservations",
                "{\"spaceId\":\"%s\",\"startAt\":\"2026-11-11T09:00:00-05:00\",\"slots\":1}".formatted(space), token),
                403, "SUSPENDED");
        assertThat(send("GET", "/reservations/mine", null, token).statusCode()).isEqualTo(200);
    }

    @Test
    void suspensionNoticeIncludesTheReactivationDate() {
        UUID userId = user("notice");
        UUID space = space();
        reservation(userId, space, "NO_SHOW", Instant.parse("2026-11-02T13:00:00Z"), Instant.parse("2026-11-02T14:00:00Z"));
        reservation(userId, space, "NO_SHOW", Instant.parse("2026-11-03T13:00:00Z"), Instant.parse("2026-11-03T14:00:00Z"));
        reservation(userId, space, "CONFIRMED", START.minus(Duration.ofHours(1)), START);

        job.closeFinishedReservations();

        assertThat(mail.bodies.get("notice@unimagdalena.edu.co")).contains("suspendida", "18/11/2026 07:00", "https://client.example/contact");
    }

    @Test
    void appliesAtMostOneSuspensionPerMonth() {
        UUID userId = user("repeat");
        UUID space = space();
        Instant previousUntil = START.plus(Duration.ofDays(3));
        jdbc.update("update app_user set suspended_until = ? where id = ?", Timestamp.from(previousUntil), userId);
        jdbc.update("insert into monthly_suspension (user_id, absence_month, created_at) values (?, date '2026-11-01', ?)",
                userId, Timestamp.from(START.minus(Duration.ofDays(4))));
        for (int day = 2; day <= 4; day++) {
            Instant start = Instant.parse("2026-11-0%dT13:00:00Z".formatted(day));
            reservation(userId, space, "NO_SHOW", start, start.plus(Duration.ofHours(1)));
        }
        UUID fourth = reservation(userId, space, "CONFIRMED", START.minus(Duration.ofHours(1)), START);

        job.closeFinishedReservations();

        assertThat(statusOf(fourth)).isEqualTo("NO_SHOW");
        assertThat(audits("USER_SUSPENDED")).isZero();
        assertThat(jdbc.queryForObject("select suspended_until from app_user where id = ?", Timestamp.class, userId)
                .toInstant()).isEqualTo(previousUntil);
        assertThat(mail.bodies.get("repeat@unimagdalena.edu.co")).contains("inasistencia");
    }

    @Test
    void countsNoShowsByCalendarMonthInBogota() {
        UUID userId = user("monthly");
        UUID space = space();
        reservation(userId, space, "NO_SHOW", Instant.parse("2026-10-20T13:00:00Z"), Instant.parse("2026-10-20T14:00:00Z"));
        // Termina el 31 de octubre a las 23:00 en Bogotá: cuenta para octubre aunque en UTC ya sea noviembre.
        reservation(userId, space, "NO_SHOW", Instant.parse("2026-11-01T03:00:00Z"), Instant.parse("2026-11-01T04:00:00Z"));
        reservation(userId, space, "NO_SHOW", Instant.parse("2026-11-02T13:00:00Z"), Instant.parse("2026-11-02T14:00:00Z"));
        reservation(userId, space, "CONFIRMED", START.minus(Duration.ofHours(1)), START);

        job.closeFinishedReservations();

        assertThat(audits("USER_SUSPENDED")).isZero();
        assertThat(jdbc.queryForObject("select suspended_until from app_user where id = ?", Timestamp.class, userId))
                .isNull();
    }

    @Test
    void usesTheThresholdInForceWhenTheNoShowIsRegistered() {
        UUID admin = user("threshold-admin");
        UUID parameters = UUID.randomUUID();
        jdbc.update("insert into usage_parameters (id, version_number, weekly_quota_hours, no_show_threshold, created_at, "
                + "created_by) values (?, 2, 4, 1, ?, ?)", parameters, Timestamp.from(START), admin);
        jdbc.update("update usage_parameters_head set current_version_id = ? where id = 1", parameters);
        UUID userId = user("threshold");
        reservation(userId, space(), "CONFIRMED", START.minus(Duration.ofHours(1)), START);

        job.closeFinishedReservations();

        assertThat(audits("USER_SUSPENDED")).isEqualTo(1);
    }

    @Test
    void concurrentRetriesRegisterTheNoShowAndTheSuspensionOnce() throws Exception {
        UUID userId = user("concurrent");
        UUID space = space();
        reservation(userId, space, "NO_SHOW", Instant.parse("2026-11-02T13:00:00Z"), Instant.parse("2026-11-02T14:00:00Z"));
        reservation(userId, space, "NO_SHOW", Instant.parse("2026-11-03T13:00:00Z"), Instant.parse("2026-11-03T14:00:00Z"));
        UUID closed = reservation(userId, space, "CONFIRMED", START.minus(Duration.ofHours(1)), START);
        var barrier = new CyclicBarrier(2);
        // Se llama al servicio directamente: el job es synchronized y no ejercitaría el bloqueo asesor.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = List.of(
                    executor.submit(() -> { barrier.await(); sanctions.markNoShow(closed); return null; }),
                    executor.submit(() -> { barrier.await(); sanctions.markNoShow(closed); return null; }));
            for (var task : tasks) task.get();
        }
        assertThat(statusOf(closed)).isEqualTo("NO_SHOW");
        assertThat(audits("NO_SHOW_REGISTERED")).isEqualTo(1);
        assertThat(audits("USER_SUSPENDED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from monthly_suspension where user_id = ?", Integer.class,
                userId)).isEqualTo(1);
        assertThat(mail.sentCount.get()).isEqualTo(2);
    }

    @Test
    void checkInAtTheLastInstantCompletesInsteadOfNoShow() throws Exception {
        String token = completeLogin("last-instant@unimagdalena.edu.co");
        UUID userId = UUID.fromString(decoder.decode(token).getSubject());
        UUID space = space();
        UUID reservation = reservation(userId, space, "CONFIRMED", START.minus(Duration.ofMinutes(50)), START.plusSeconds(1));
        assertThat(send("POST", "/checkin/" + space, null, token).statusCode()).isEqualTo(200);
        clock.advance(Duration.ofSeconds(1));

        job.closeFinishedReservations();

        assertThat(statusOf(reservation)).isEqualTo("COMPLETED");
        assertThat(audits("NO_SHOW_REGISTERED")).isZero();
    }

    @Test
    void suspensionReleasesSpaceAndEquipmentAndNotifiesAvailability() throws Exception {
        UUID userId = user("release");
        UUID space = space();
        var item = equipment("Balón de baloncesto", 1);
        reservation(userId, space, "NO_SHOW", Instant.parse("2026-11-02T13:00:00Z"), Instant.parse("2026-11-02T14:00:00Z"));
        reservation(userId, space, "NO_SHOW", Instant.parse("2026-11-03T13:00:00Z"), Instant.parse("2026-11-03T14:00:00Z"));
        reservation(userId, space, "CONFIRMED", START.minus(Duration.ofHours(1)), START);
        Instant futureStart = Instant.parse("2026-11-12T12:00:00Z");
        UUID future = reservation(userId, space, "CONFIRMED", futureStart, futureStart.plus(Duration.ofHours(1)));
        jdbc.update("insert into reservation_equipment (reservation_id, equipment_id, quantity) values (?, ?, 1)",
                future, item.id());
        events.clear();

        job.closeFinishedReservations();

        assertThat(statusOf(future)).isEqualTo("CANCELLED_BY_SUSPENSION");
        assertThat(events.stream(AvailabilityChangedEvent.class)).containsExactly(new AvailabilityChangedEvent(space));
        assertThat(mail.bodies.get("release@unimagdalena.edu.co")).contains("Motivo: suspensión");
        String other = completeLogin("release-other@unimagdalena.edu.co");
        var rebooked = send("POST", "/reservations", """
                {"spaceId":"%s","startAt":"2026-11-12T07:00:00-05:00","slots":1,
                 "equipment":[{"equipmentId":"%s","quantity":1}]}
                """.formatted(space, item.id()), other);
        assertThat(rebooked.statusCode()).isEqualTo(201);
    }

    @Test
    void countRestartsAfterAPreviousMonthSuspensionAndContainmentIsInclusiveAtTheEnd() {
        UUID userId = user("new-month");
        UUID space = space();
        jdbc.update("insert into monthly_suspension (user_id, absence_month, created_at) values (?, date '2026-10-01', ?)",
                userId, Timestamp.from(Instant.parse("2026-10-20T12:00:00Z")));
        jdbc.update("update app_user set suspended_until = ? where id = ?",
                Timestamp.from(Instant.parse("2026-10-27T12:00:00Z")), userId);
        reservation(userId, space, "NO_SHOW", Instant.parse("2026-11-02T13:00:00Z"), Instant.parse("2026-11-02T14:00:00Z"));
        reservation(userId, space, "NO_SHOW", Instant.parse("2026-11-03T13:00:00Z"), Instant.parse("2026-11-03T14:00:00Z"));
        reservation(userId, space, "CONFIRMED", START.minus(Duration.ofHours(1)), START);
        Instant until = START.plus(Duration.ofDays(7));
        UUID startsNow = reservation(userId, space, "CONFIRMED", START, START.plus(Duration.ofHours(1)));
        UUID endsAtUntil = reservation(userId, space, "CONFIRMED", until.minus(Duration.ofHours(1)), until);

        job.closeFinishedReservations();

        assertThat(audits("USER_SUSPENDED")).isEqualTo(1);
        assertThat(statusOf(startsNow)).isEqualTo("CONFIRMED");
        assertThat(statusOf(endsAtUntil)).isEqualTo("CANCELLED_BY_SUSPENSION");
    }

    @Test
    void mailFailureDoesNotRollbackTheNoShow() {
        UUID closed = reservation(user("mail-failure"), space(), "CONFIRMED", START.minus(Duration.ofHours(1)), START);
        mail.failing = true;

        job.closeFinishedReservations();

        assertThat(statusOf(closed)).isEqualTo("NO_SHOW");
    }

    private UUID user(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into app_user (id, email, name, role, created_at) values (?, ?, ?, 'USER', ?)",
                id, name + "@unimagdalena.edu.co", name, Timestamp.from(START));
        return id;
    }

    private UUID reservation(UUID userId, UUID spaceId, String status, Instant start, Instant end) {
        return reservation(userId, spaceId, status, start, end, start.minus(Duration.ofDays(1)), null);
    }

    private UUID reservation(UUID userId, UUID spaceId, String status, Instant start, Instant end, Instant created,
            Instant checkedIn) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into reservation (id, user_id, space_id, start_at, end_at, status, created_at, checked_in_at, "
                        + "cancellation_threshold_minutes, checkin_opening_minutes) values (?, ?, ?, ?, ?, ?, ?, ?, 60, 10)",
                id, userId, spaceId, Timestamp.from(start), Timestamp.from(end), status, Timestamp.from(created),
                checkedIn == null ? null : Timestamp.from(checkedIn));
        return id;
    }

    private long usedHours(UUID userId) {
        return quota.usedInWeek(userId, WEEK_START, WEEK_START.plus(Duration.ofDays(7))).toHours();
    }

    private String statusOf(UUID reservation) {
        return jdbc.queryForObject("select status from reservation where id = ?", String.class, reservation);
    }

    private int audits(String action) {
        return jdbc.queryForObject("select count(*) from audit_log where action = ?", Integer.class, action);
    }
}
