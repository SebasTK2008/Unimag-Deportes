package unimagdeportes.contract.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import unimagdeportes.support.ReservationTestSupport;

class IndicatorsContractTest extends ReservationTestSupport {

    @Test
    void returnsTheIndicatorsOfTheSelectedPeriod() throws Exception {
        String admin = completeLogin("admin@unimagdalena.edu.co");

        var weekly = send("GET", "/admin/indicators?period=WEEKLY", null, admin);
        assertThat(weekly.statusCode()).isEqualTo(200);
        assertThat(body(weekly)).isEqualTo(json.readTree("""
                {"period":"WEEKLY","from":"2026-11-09T00:00:00-05:00","to":"2026-11-16T00:00:00-05:00",
                 "reservations":0,"lateCancellations":0,"noShows":0,"suspendedUsers":0}
                """));

        var monthly = send("GET", "/admin/indicators?period=MONTHLY", null, admin);
        assertThat(monthly.statusCode()).isEqualTo(200);
        assertThat(body(monthly).get("from").asString()).isEqualTo("2026-11-01T00:00:00-05:00");
        assertThat(body(monthly).get("to").asString()).isEqualTo("2026-12-01T00:00:00-05:00");
    }

    @Test
    void rejectsMissingOrUnknownPeriods() throws Exception {
        String admin = completeLogin("admin@unimagdalena.edu.co");
        error(send("GET", "/admin/indicators", null, admin), 400, "VALIDATION_ERROR");
        for (String invalid : new String[]{"DAILY", "weekly", ""}) {
            var response = send("GET", "/admin/indicators?period=" + invalid, null, admin);
            error(response, 400, "INVALID_PERIOD");
            assertThat(body(response).get("details").get("field").asString()).isEqualTo("period");
        }
    }

    @Test
    void requiresTheCurrentAdministratorRole() throws Exception {
        error(send("GET", "/admin/indicators?period=WEEKLY", null, null), 401, "TOKEN_INVALID");
        String user = completeLogin("indicators-user@unimagdalena.edu.co");
        error(send("GET", "/admin/indicators?period=WEEKLY", null, user), 403, "FORBIDDEN");
        String admin = completeLogin("admin@unimagdalena.edu.co");
        clock.advance(Duration.ofHours(24));
        error(send("GET", "/admin/indicators?period=WEEKLY", null, admin), 401, "TOKEN_EXPIRED");
    }
}
