package unimagdeportes.integration.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.node.ObjectNode;
import unimagdeportes.support.RulesTestSupport;

@TestPropertySource(properties = {
        "app.rules.booking-tolerance-minutes=15", "app.rules.late-cancellation-minutes=90",
        "app.rules.min-advance-minutes=5", "app.rules.checkin-opening-minutes=20", "app.rules.suspension-days=14" })
class ConfiguredRulesTest extends RulesTestSupport {
    @Test
    void readsDeploymentSettingsAlongsideDatabaseValuesWithoutMakingThemEditable() throws Exception {
        String admin = administrator();
        jdbc.update("update usage_parameters set weekly_quota_hours = 8, no_show_threshold = 4 where id = 1");
        var expected = expectedRules(8, 4);
        ((ObjectNode) expected.get("reservation")).put("bookingToleranceMinutes", 15).put("minAdvanceMinutes", 5);
        ((ObjectNode) expected.get("cancellation")).put("lateThresholdMinutes", 90);
        ((ObjectNode) expected.get("checkIn")).put("opensMinutesBefore", 20);
        ((ObjectNode) expected.get("sanctions")).put("suspensionDays", 14);
        for (String path : new String[]{"/rules", "/admin/parameters"}) {
            var response = send("GET", path, null, admin);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(body(response)).isEqualTo(expected);
        }
        var updated = send("PUT", "/admin/parameters", "{\"quota\":{\"weeklyHours\":5}}", admin);
        ((ObjectNode) expected.get("quota")).put("weeklyHours", 5);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(body(updated)).isEqualTo(expected);
    }
}
