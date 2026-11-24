package unimagdeportes.integration.quota;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import unimagdeportes.support.ReservationTestSupport;

class QuotaIntegrationTest extends ReservationTestSupport {
    private static final String EMAIL = "quota-user@unimagdalena.edu.co";

    @Test
    void countsHoursAccordingToTheReservationStatus() throws Exception {
        Map<String, Integer> expectedHours = new LinkedHashMap<>();
        expectedHours.put("CONFIRMED", 1);
        expectedHours.put("IN_USE", 1);
        expectedHours.put("COMPLETED", 1);
        expectedHours.put("CANCELLED_LATE", 1);
        expectedHours.put("NO_SHOW", 1);
        expectedHours.put("CANCELLED_ON_TIME", 0);
        expectedHours.put("CANCELLED_BY_ADMIN", 0);
        expectedHours.put("CANCELLED_BY_SUSPENSION", 0);
        String user = completeLogin(EMAIL);
        UUID space = space();

        for (var expected : expectedHours.entrySet()) {
            jdbc.update("delete from reservation where user_id = ?", userId(user));
            fixtureReservation(userId(user), space, expected.getKey(),
                    Instant.parse("2026-11-12T13:00:00Z"), Instant.parse("2026-11-12T14:00:00Z"));

            var quota = quota(user);
            assertThat(quota.get("usedHours").asInt()).as(expected.getKey()).isEqualTo(expected.getValue());
            assertThat(quota.get("availableHours").asInt()).as(expected.getKey()).isEqualTo(4 - expected.getValue());
        }
    }

    @Test
    void onTimeCancellationReturnsTheHourAndLateCancellationKeepsIt() throws Exception {
        String user = completeLogin(EMAIL);
        UUID onTime = reserve(user, space(), "2026-11-11T08:00:00-05:00");
        assertThat(quota(user).get("usedHours").asInt()).isEqualTo(1);
        assertThat(send("POST", "/reservations/" + onTime + "/cancellation", null, user).statusCode()).isEqualTo(200);
        assertThat(quota(user).get("usedHours").asInt()).isZero();

        UUID late = reserve(user, space(), "2026-11-11T07:00:00-05:00");
        assertThat(send("POST", "/reservations/" + late + "/cancellation", null, user).statusCode()).isEqualTo(200);
        assertThat(quota(user).get("usedHours").asInt()).isEqualTo(1);
        assertThat(quota(user).get("availableHours").asInt()).isEqualTo(3);
    }

    @Test
    void availableHoursMatchAcceptanceAndRejectionOfNewReservations() throws Exception {
        String user = completeLogin(EMAIL);
        UUID other = space();
        fixtureReservation(userId(user), other, "CONFIRMED",
                Instant.parse("2026-11-13T13:00:00Z"), Instant.parse("2026-11-13T16:00:00Z"));
        assertThat(quota(user).get("availableHours").asInt()).isEqualTo(1);
        UUID space = space();

        error(send("POST", "/reservations",
                "{\"spaceId\":\"%s\",\"startAt\":\"2026-11-11T08:00:00-05:00\",\"slots\":2}".formatted(space), user),
                409, "QUOTA_EXCEEDED");
        reserve(user, space, "2026-11-11T08:00:00-05:00");

        var quota = quota(user);
        assertThat(quota.get("usedHours").asInt()).isEqualTo(4);
        assertThat(quota.get("availableHours").asInt()).isZero();
    }

    @Test
    void resetsAtMondayMidnightInBogotaCountingReservationsMadeInAdvance() throws Exception {
        UUID userId = userId(completeLogin(EMAIL));
        UUID space = space();
        fixtureReservation(userId, space, "COMPLETED",
                Instant.parse("2026-11-10T13:00:00Z"), Instant.parse("2026-11-10T15:00:00Z"));
        fixtureReservation(userId, space, "CONFIRMED",
                Instant.parse("2026-11-17T13:00:00Z"), Instant.parse("2026-11-17T14:00:00Z"));

        clock.set(Instant.parse("2026-11-16T04:59:59Z"));
        String token = completeLogin(EMAIL);
        var lastSecond = quota(token);
        assertThat(lastSecond.get("weekStart").asString()).isEqualTo("2026-11-09T00:00:00-05:00");
        assertThat(lastSecond.get("usedHours").asInt()).isEqualTo(2);
        assertThat(lastSecond.get("resetAt").asString()).isEqualTo("2026-11-16T00:00:00-05:00");

        clock.set(Instant.parse("2026-11-16T05:00:00Z"));
        var newWeek = quota(token);
        assertThat(newWeek.get("weekStart").asString()).isEqualTo("2026-11-16T00:00:00-05:00");
        assertThat(newWeek.get("usedHours").asInt()).isEqualTo(1);
        assertThat(newWeek.get("availableHours").asInt()).isEqualTo(3);
        assertThat(newWeek.get("resetAt").asString()).isEqualTo("2026-11-23T00:00:00-05:00");
    }

    @Test
    void splitsAReservationThatCrossesTheStartOfTheWeek() throws Exception {
        UUID userId = userId(completeLogin(EMAIL));
        fixtureReservation(userId, space(), "CONFIRMED",
                Instant.parse("2026-11-16T04:00:00Z"), Instant.parse("2026-11-16T06:00:00Z"));

        clock.set(Instant.parse("2026-11-15T12:00:00Z"));
        assertThat(quota(completeLogin(EMAIL)).get("usedHours").asInt()).isEqualTo(1);
        clock.set(Instant.parse("2026-11-16T12:00:00Z"));
        assertThat(quota(completeLogin(EMAIL)).get("usedHours").asInt()).isEqualTo(1);
    }

    /** Escenario A de la sección 9 del contexto, con límite de 4 h. */
    @Test
    void reproducesTheWeeklyQuotaScenarioOfTheContext() throws Exception {
        clock.set(Instant.parse("2026-11-11T10:00:00Z"));
        String ana = completeLogin(EMAIL);
        fixtureReservation(userId(ana), space(), "COMPLETED",
                Instant.parse("2026-11-09T13:00:00Z"), Instant.parse("2026-11-09T15:00:00Z"));
        assertThat(quota(ana).get("usedHours").asInt()).isEqualTo(2);

        UUID wednesday = reserve(ana, space(), "2026-11-11T10:00:00-05:00");
        assertThat(quota(ana).get("usedHours").asInt()).isEqualTo(3);

        UUID space = space();
        error(send("POST", "/reservations",
                "{\"spaceId\":\"%s\",\"startAt\":\"2026-11-12T08:00:00-05:00\",\"slots\":2}".formatted(space), ana),
                409, "QUOTA_EXCEEDED");

        var cancelled = send("POST", "/reservations/" + wednesday + "/cancellation", null, ana);
        assertThat(body(cancelled).get("status").asString()).isEqualTo("CANCELLED_ON_TIME");
        assertThat(quota(ana).get("usedHours").asInt()).isEqualTo(2);

        reserve(ana, space, "2026-11-12T08:00:00-05:00");
        var quota = quota(ana);
        assertThat(quota.get("usedHours").asInt()).isEqualTo(3);
        assertThat(quota.get("availableHours").asInt()).isEqualTo(1);
    }

    @Test
    void quotaIsDerivedWithoutAPersistedBalance() {
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns "
                + "where table_schema = current_schema() and (table_name like '%quota%' "
                + "or (column_name like '%quota%' and table_name <> 'usage_parameters'))", Integer.class)).isZero();
    }

    private JsonNode quota(String token) throws Exception {
        var response = send("GET", "/quota/me", null, token);
        assertThat(response.statusCode()).isEqualTo(200);
        return body(response);
    }

    private UUID reserve(String token, UUID space, String startAt) throws Exception {
        var created = send("POST", "/reservations",
                "{\"spaceId\":\"%s\",\"startAt\":\"%s\",\"slots\":1}".formatted(space, startAt), token);
        assertThat(created.statusCode()).isEqualTo(201);
        return UUID.fromString(body(created).get("id").asString());
    }
}
