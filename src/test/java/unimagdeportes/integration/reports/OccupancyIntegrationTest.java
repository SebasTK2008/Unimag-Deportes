package unimagdeportes.integration.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import unimagdeportes.support.ReservationTestSupport;

class OccupancyIntegrationTest extends ReservationTestSupport {

    @Test
    void sharedSpaceAppearsOccupiedForEveryDiscipline() throws Exception {
        String admin = completeLogin("admin@unimagdalena.edu.co");
        UUID shared = space("Coliseo", "VOLLEYBALL", "BASKETBALL");
        fixtureReservation(fixtureUser("shared"), shared, "CONFIRMED",
                Instant.parse("2026-11-11T13:00:00Z"), Instant.parse("2026-11-11T14:00:00Z"));

        for (String discipline : new String[]{"VOLLEYBALL", "BASKETBALL"}) {
            var spaces = spaces(admin, "date=2026-11-11&discipline=" + discipline);
            var coliseo = find(spaces, shared);
            assertThat(coliseo).as(discipline).isNotNull();
            assertThat(coliseo.get("slots").get(1).get("status").asString()).isEqualTo("OCCUPIED");
        }
        assertThat(find(spaces(admin, "date=2026-11-11&discipline=TENNIS"), shared)).isNull();
    }

    @Test
    void filtersByDisciplineSpaceAndDate() throws Exception {
        String admin = completeLogin("admin@unimagdalena.edu.co");
        UUID tennis = space("Tenis 1", "TENNIS");
        UUID football = space("Fútbol 5", "FOOTBALL_5");
        fixtureReservation(fixtureUser("filters"), tennis, "COMPLETED",
                Instant.parse("2026-11-10T12:00:00Z"), Instant.parse("2026-11-10T13:00:00Z"));

        var byDiscipline = spaces(admin, "date=2026-11-10&discipline=TENNIS");
        assertThat(find(byDiscipline, tennis)).isNotNull();
        assertThat(find(byDiscipline, football)).isNull();
        for (JsonNode space : byDiscipline) {
            assertThat(jdbc.queryForObject("select count(*) from space_discipline where space_id = ? "
                    + "and discipline = 'TENNIS'", Integer.class, UUID.fromString(space.get("spaceId").asString())))
                    .isEqualTo(1);
        }

        var bySpace = spaces(admin, "date=2026-11-10&spaceId=" + football);
        assertThat(bySpace.size()).isEqualTo(1);
        assertThat(bySpace.get(0).get("spaceId").asString()).isEqualTo(football.toString());

        var pastDay = find(spaces(admin, "date=2026-11-10&spaceId=" + tennis), tennis);
        assertThat(statuses(pastDay)).containsExactly("OCCUPIED", "FREE", "FREE", "FREE");
        var otherDay = find(spaces(admin, "date=2026-11-12&spaceId=" + tennis), tennis);
        assertThat(statuses(otherDay)).containsExactly("FREE", "FREE", "FREE", "FREE");
    }

    @Test
    void reservationsThatReleaseTheSpaceAreNotShownAsOccupied() throws Exception {
        String admin = completeLogin("admin@unimagdalena.edu.co");
        UUID space = space();
        UUID user = fixtureUser("released");
        String[] released = {"CANCELLED_ON_TIME", "CANCELLED_LATE", "CANCELLED_BY_ADMIN", "CANCELLED_BY_SUSPENSION"};
        for (int hour = 0; hour < released.length; hour++) {
            fixtureReservation(user, space, released[hour], Instant.parse("2026-11-11T12:00:00Z").plusSeconds(3600L * hour),
                    Instant.parse("2026-11-11T13:00:00Z").plusSeconds(3600L * hour));
        }

        var slots = find(spaces(admin, "date=2026-11-11&spaceId=" + space), space);
        assertThat(statuses(slots)).containsOnly("FREE");
    }

    private JsonNode spaces(String token, String query) throws Exception {
        var response = send("GET", "/admin/occupancy?" + query, null, token);
        assertThat(response.statusCode()).isEqualTo(200);
        return body(response).get("spaces");
    }

    private JsonNode find(JsonNode spaces, UUID id) {
        for (JsonNode space : spaces) {
            if (space.get("spaceId").asString().equals(id.toString())) return space;
        }
        return null;
    }

    private List<String> statuses(JsonNode space) {
        List<String> result = new ArrayList<>();
        space.get("slots").forEach(slot -> result.add(slot.get("status").asString()));
        return result;
    }
}
