package unimagdeportes.integration.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import unimagdeportes.scheduler.ReservationLifecycleJob;
import unimagdeportes.support.ReservationTestSupport;

class IndicatorsIntegrationTest extends ReservationTestSupport {
    private static final Instant THIS_WEEK = Instant.parse("2026-11-10T15:00:00Z");
    private static final Instant EARLIER_THIS_MONTH = Instant.parse("2026-11-03T15:00:00Z");
    @Autowired ReservationLifecycleJob job;

    @Test
    void countsAuditedEventsInsideEachPeriod() throws Exception {
        UUID space = space();
        UUID user = fixtureUser("indicators");
        UUID other = fixtureUser("indicators-other");
        fixtureAudit("RESERVATION_CREATED", "RESERVATION", UUID.randomUUID(), Instant.parse("2026-11-09T05:00:00Z"));
        fixtureAudit("RESERVATION_CREATED", "RESERVATION", UUID.randomUUID(), THIS_WEEK);
        fixtureAudit("RESERVATION_CREATED", "RESERVATION", UUID.randomUUID(), EARLIER_THIS_MONTH);
        fixtureAudit("RESERVATION_CREATED", "RESERVATION", UUID.randomUUID(), Instant.parse("2026-11-01T04:59:59Z"));
        fixtureAudit("RESERVATION_CREATED", "RESERVATION", UUID.randomUUID(), Instant.parse("2026-11-16T05:00:00Z"));
        cancellation(user, space, "CANCELLED_LATE", THIS_WEEK, 1);
        cancellation(user, space, "CANCELLED_ON_TIME", THIS_WEEK, 2);
        cancellation(user, space, "CANCELLED_LATE", EARLIER_THIS_MONTH, 3);
        fixtureAudit("NO_SHOW_REGISTERED", "RESERVATION", UUID.randomUUID(), THIS_WEEK);
        fixtureAudit("NO_SHOW_REGISTERED", "RESERVATION", UUID.randomUUID(), EARLIER_THIS_MONTH);
        fixtureAudit("USER_SUSPENDED", "USER", user, THIS_WEEK);
        fixtureAudit("USER_SUSPENDED", "USER", user, THIS_WEEK.plusSeconds(60));
        fixtureAudit("USER_SUSPENDED", "USER", other, EARLIER_THIS_MONTH);
        suspension(user, "2026-11-01", THIS_WEEK, THIS_WEEK.plus(Duration.ofDays(7)));
        suspension(user, "2026-10-01", THIS_WEEK.plusSeconds(60), THIS_WEEK.plus(Duration.ofDays(8)));
        suspension(other, "2026-09-01", EARLIER_THIS_MONTH, EARLIER_THIS_MONTH.plus(Duration.ofDays(2)));
        fixtureAudit("LOGIN", "APP_USER", user, THIS_WEEK);
        String admin = completeLogin("admin@unimagdalena.edu.co");

        assertIndicators(indicators(admin, "WEEKLY"), 2, 1, 1, 1);
        // El evento del lunes 16 queda fuera de la semana, pero dentro del mes.
        assertIndicators(indicators(admin, "MONTHLY"), 4, 2, 2, 2);
    }

    @Test
    void countsDistinctUsersWhoseSuspensionsOverlapThePeriod() throws Exception {
        UUID before = fixtureUser("overlap-before");
        UUID endsAtStart = fixtureUser("overlap-end");
        UUID startsAtEnd = fixtureUser("overlap-start");
        Instant weekStart = Instant.parse("2026-11-09T05:00:00Z");
        Instant weekEnd = Instant.parse("2026-11-16T05:00:00Z");
        suspension(before, "2026-10-01", weekStart.minusSeconds(60), weekStart.plusSeconds(60));
        suspension(before, "2026-11-01", weekStart, weekEnd);
        suspension(endsAtStart, "2026-11-01", weekStart.minusSeconds(60), weekStart);
        suspension(startsAtEnd, "2026-11-01", weekEnd, weekEnd.plusSeconds(60));
        String admin = completeLogin("admin@unimagdalena.edu.co");
        assertThat(indicators(admin, "WEEKLY").get("suspendedUsers").asInt()).isEqualTo(1);
        assertThat(indicators(admin, "MONTHLY").get("suspendedUsers").asInt()).isEqualTo(3);
    }

    private void suspension(UUID user, String month, Instant start, Instant end) {
        jdbc.update("insert into monthly_suspension (user_id, absence_month, created_at, suspended_until) "
                        + "values (?, cast(? as date), ?, ?)", user, month,
                java.sql.Timestamp.from(start), java.sql.Timestamp.from(end));
    }

    @Test
    void reflectsLateCancellationsNoShowsAndSuspensionsProducedByTheirFlows() throws Exception {
        String user = completeLogin("indicators-flow@unimagdalena.edu.co");
        UUID userId = userId(user);
        UUID space = space();
        var created = send("POST", "/reservations",
                "{\"spaceId\":\"%s\",\"startAt\":\"2026-11-11T07:00:00-05:00\",\"slots\":1}".formatted(space), user);
        assertThat(created.statusCode()).isEqualTo(201);
        String reservation = body(created).get("id").asString();
        assertThat(send("POST", "/reservations/" + reservation + "/cancellation", null, user).statusCode())
                .isEqualTo(200);
        fixtureReservation(userId, space, "NO_SHOW", Instant.parse("2026-11-02T13:00:00Z"), Instant.parse("2026-11-02T14:00:00Z"));
        fixtureReservation(userId, space, "NO_SHOW", Instant.parse("2026-11-03T13:00:00Z"), Instant.parse("2026-11-03T14:00:00Z"));
        fixtureReservation(userId, space(), "CONFIRMED", START.minus(Duration.ofHours(2)), START.minus(Duration.ofHours(1)));
        job.closeFinishedReservations();
        String admin = completeLogin("admin@unimagdalena.edu.co");

        assertIndicators(indicators(admin, "WEEKLY"), 1, 1, 1, 1);
    }

    private void cancellation(UUID user, UUID space, String status, Instant at, int hour) {
        Instant start = Instant.parse("2026-11-20T12:00:00Z").plus(Duration.ofHours(hour));
        UUID reservation = fixtureReservation(user, space, status, start, start.plus(Duration.ofHours(1)));
        fixtureAudit("RESERVATION_CANCELLED", "RESERVATION", reservation, at);
    }

    private JsonNode indicators(String token, String period) throws Exception {
        var response = send("GET", "/admin/indicators?period=" + period, null, token);
        assertThat(response.statusCode()).isEqualTo(200);
        return body(response);
    }

    private void assertIndicators(JsonNode indicators, int reservations, int lateCancellations, int noShows,
            int suspendedUsers) {
        assertThat(indicators.get("reservations").asInt()).isEqualTo(reservations);
        assertThat(indicators.get("lateCancellations").asInt()).isEqualTo(lateCancellations);
        assertThat(indicators.get("noShows").asInt()).isEqualTo(noShows);
        assertThat(indicators.get("suspendedUsers").asInt()).isEqualTo(suspendedUsers);
    }
}
