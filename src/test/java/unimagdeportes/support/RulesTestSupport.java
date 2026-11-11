package unimagdeportes.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import tools.jackson.databind.JsonNode;

public abstract class RulesTestSupport extends AuthTestSupport {
    @BeforeEach
    @AfterEach
    void resetParameters() {
        jdbc.update("update usage_parameters set weekly_quota_hours = 4, no_show_threshold = 3 where id = 1");
    }

    protected String reauthenticate(String token) throws Exception {
        clock.advance(Duration.ofSeconds(60));
        assertThat(send("POST", "/auth/reauth/code", null, token).statusCode()).isEqualTo(202);
        var response = send("POST", "/auth/reauth/verify",
                "{\"code\":\"" + code("admin@unimagdalena.edu.co") + "\"}", token);
        assertThat(response.statusCode()).isEqualTo(200);
        return body(response).get("token").asString();
    }

    protected String administrator() throws Exception {
        return reauthenticate(completeLogin("admin@unimagdalena.edu.co"));
    }

    protected JsonNode expectedRules(int weeklyHours, int noShows) {
        return json.readTree("""
                {
                  "reservation": {"slotMinutes":60,"maxSlotsPerReservation":2,"maxAdvanceDays":7,
                    "minAdvanceMinutes":0,"bookingToleranceMinutes":10},
                  "quota": {"weeklyHours":%d,"weekStartsOn":"MONDAY","timeZone":"America/Bogota"},
                  "cancellation": {"lateThresholdMinutes":60},
                  "checkIn": {"opensMinutesBefore":10,"closesAtReservationEnd":true},
                  "reminder": {"hoursBefore":2},
                  "sanctions": {"noShowsForSuspension":%d,"window":"CALENDAR_MONTH","suspensionDays":7},
                  "emailDomain":"@unimagdalena.edu.co"
                }
                """.formatted(weeklyHours, noShows));
    }

    protected int updateAuditCount() {
        return jdbc.queryForObject("select count(*) from audit_log where action = 'PARAMETERS_UPDATED'", Integer.class);
    }
}
