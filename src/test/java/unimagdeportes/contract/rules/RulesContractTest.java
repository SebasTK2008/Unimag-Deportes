package unimagdeportes.contract.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import unimagdeportes.support.RulesTestSupport;

class RulesContractTest extends RulesTestSupport {
    @Test
    void readsAllDefaultBusinessRulesForBothRolesWithoutAuthenticationSettings() throws Exception {
        String user = completeLogin("reader@unimagdalena.edu.co");
        String admin = completeLogin("admin@unimagdalena.edu.co");
        for (String token : new String[]{user, admin}) {
            var response = send("GET", "/rules", null, token);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(body(response)).isEqualTo(expectedRules(4, 3));
        }
        var parameters = send("GET", "/admin/parameters", null, admin);
        assertThat(parameters.statusCode()).isEqualTo(200);
        assertThat(body(parameters)).isEqualTo(expectedRules(4, 3));
        assertThat(updateAuditCount()).isZero();
    }

    @Test
    void partialUpdatesPreserveOmittedValuesAndReturnTheCompleteObject() throws Exception {
        String admin = administrator();
        var quota = send("PUT", "/admin/parameters", "{\"quota\":{\"weeklyHours\":5}}", admin);
        assertThat(quota.statusCode()).isEqualTo(200);
        assertThat(body(quota)).isEqualTo(expectedRules(5, 3));
        var sanctions = send("PUT", "/admin/parameters", "{\"sanctions\":{\"noShowsForSuspension\":2}}", admin);
        assertThat(sanctions.statusCode()).isEqualTo(200);
        assertThat(body(sanctions)).isEqualTo(expectedRules(5, 2));
        var emptyBlocks = send("PUT", "/admin/parameters", "{\"quota\":{},\"sanctions\":{}}", admin);
        assertThat(emptyBlocks.statusCode()).isEqualTo(200);
        assertThat(body(emptyBlocks)).isEqualTo(expectedRules(5, 2));
        assertThat(body(send("GET", "/rules", null, admin))).isEqualTo(expectedRules(5, 2));
        assertThat(body(send("GET", "/admin/parameters", null, admin))).isEqualTo(expectedRules(5, 2));
        assertThat(versionCount()).isEqualTo(3);
        assertThat(updateAuditCount()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidRangesAndJsonTypesWithoutWritingParametersOrAudit() throws Exception {
        String admin = administrator();
        for (String invalid : new String[]{"0", "-1", "1.5", "\"5\"", "true", "[]", "{}", "2147483648"}) {
            error(send("PUT", "/admin/parameters", "{\"quota\":{\"weeklyHours\":" + invalid + "}}", admin),
                    400, "VALIDATION_ERROR");
            error(send("PUT", "/admin/parameters", "{\"quota\":{\"weeklyHours\":5},"
                    + "\"sanctions\":{\"noShowsForSuspension\":" + invalid + "}}", admin), 400, "VALIDATION_ERROR");
        }
        for (String malformed : new String[]{"{", "[]", "null", "{\"quota\":5}"}) {
            error(send("PUT", "/admin/parameters", malformed, admin), 400, "VALIDATION_ERROR");
        }
        assertThat(body(send("GET", "/rules", null, admin))).isEqualTo(expectedRules(4, 3));
        assertThat(updateAuditCount()).isZero();
    }

    @Test
    void acceptsThePositiveIntegerBoundaryForBothParameters() throws Exception {
        String admin = administrator();
        var response = send("PUT", "/admin/parameters",
                "{\"quota\":{\"weeklyHours\":1},\"sanctions\":{\"noShowsForSuspension\":1}}", admin);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body(response)).isEqualTo(expectedRules(1, 1));
    }

    @Test
    void cannotChangeFixedDeploymentOrAuthenticationValues() throws Exception {
        String admin = administrator();
        var response = send("PUT", "/admin/parameters", """
                {"quota":{"weeklyHours":5,"timeZone":"UTC","weekStartsOn":"SUNDAY"},
                 "sanctions":{"suspensionDays":1},"reservation":{"slotMinutes":30},
                 "checkIn":{"opensMinutesBefore":0},"emailDomain":"@example.com","auth":{"jwt":"secret"}}
                """, admin);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body(response)).isEqualTo(expectedRules(5, 3));
        assertThat(body(send("GET", "/admin/parameters", null, admin))).isEqualTo(expectedRules(5, 3));
    }

    @Test
    void enforcesAuthenticationCurrentRoleAndProfile() throws Exception {
        String user = completeLogin("user@unimagdalena.edu.co");
        for (String path : new String[]{"/rules", "/admin/parameters"}) {
            error(send("GET", path, null, null), 401, "TOKEN_INVALID");
            error(send("GET", path, null, "invalid-token"), 401, "TOKEN_INVALID");
        }
        error(send("PUT", "/admin/parameters", "{}", null), 401, "TOKEN_INVALID");
        error(send("GET", "/admin/parameters", null, user), 403, "FORBIDDEN");
        error(send("PUT", "/admin/parameters", "{}", user), 403, "FORBIDDEN");
        String incomplete = login("incomplete@unimagdalena.edu.co");
        error(send("GET", "/rules", null, incomplete), 403, "PROFILE_INCOMPLETE");
        String admin = administrator();
        jdbc.update("update app_user set role = 'USER' where email = 'admin@unimagdalena.edu.co'");
        error(send("PUT", "/admin/parameters", "{}", admin), 403, "FORBIDDEN");
        clock.advance(Duration.ofHours(24));
        error(send("GET", "/rules", null, user), 401, "TOKEN_EXPIRED");
        error(send("GET", "/admin/parameters", null, admin), 401, "TOKEN_EXPIRED");
        error(send("PUT", "/admin/parameters", "{}", admin), 401, "TOKEN_EXPIRED");
        assertThat(updateAuditCount()).isZero();
    }
}
