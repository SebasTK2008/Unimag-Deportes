package unimagdeportes.integration.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import unimagdeportes.service.parameter.ParameterService;
import unimagdeportes.support.RulesTestSupport;

class ParameterIntegrationTest extends RulesTestSupport {
    @Autowired ParameterService parameters;

    @Test
    void reauthenticationControlsWritesAtTheExactTenMinuteBoundary() throws Exception {
        String original = completeLogin("admin@unimagdalena.edu.co");
        String payload = "{\"quota\":{\"weeklyHours\":6}}";
        var denied = send("PUT", "/admin/parameters", payload, original);
        error(denied, 403, "REAUTH_REQUIRED");
        assertThat(body(denied).get("message").asString()).isEqualTo("Debes reautenticarte para cambiar las reglas.");
        assertThat(body(denied).get("details").isObject()).isTrue();
        assertThat(parameters.current().weeklyQuotaHours()).isEqualTo(4);
        String recent = reauthenticate(original);
        assertThat(decoder.decode(recent).getExpiresAt()).isEqualTo(decoder.decode(original).getExpiresAt());
        clock.advance(Duration.ofMinutes(10).minusSeconds(1));
        assertThat(send("PUT", "/admin/parameters", payload, recent).statusCode()).isEqualTo(200);
        clock.advance(Duration.ofSeconds(1));
        error(send("PUT", "/admin/parameters", "{\"quota\":{\"weeklyHours\":7}}", recent), 403, "REAUTH_REQUIRED");
        assertThat(body(send("GET", "/admin/parameters", null, original))).isEqualTo(expectedRules(6, 3));
        assertThat(updateAuditCount()).isEqualTo(1);
    }

    @Test
    void rejectsFutureReauthenticationWithoutWriting() throws Exception {
        String admin = completeLogin("admin@unimagdalena.edu.co");
        var decoded = decoder.decode(admin);
        var claims = JwtClaimsSet.builder().claims(values -> values.putAll(decoded.getClaims()))
                .claim("reauth_at", clock.instant().plusSeconds(1).getEpochSecond()).build();
        String future = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        error(send("PUT", "/admin/parameters", "{\"quota\":{\"weeklyHours\":9}}", future), 403, "REAUTH_REQUIRED");
        assertThat(parameters.current().weeklyQuotaHours()).isEqualTo(4);
        assertThat(updateAuditCount()).isZero();
    }

    @Test
    void persistsBothValuesAndAttributesAuditToTheAdministrator() throws Exception {
        String admin = administrator();
        var response = send("PUT", "/admin/parameters",
                "{\"quota\":{\"weeklyHours\":5},\"sanctions\":{\"noShowsForSuspension\":2}}", admin);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(parameters.current()).isEqualTo(new ParameterService.CurrentParameters(5, 2));
        assertThat(body(send("GET", "/rules", null, admin))).isEqualTo(expectedRules(5, 2));
        var audit = jdbc.queryForMap("select * from audit_log where action = 'PARAMETERS_UPDATED'");
        assertThat(audit.get("entity_type")).isEqualTo("USAGE_PARAMETERS");
        assertThat(audit.get("user_id")).isEqualTo(UUID.fromString(decoder.decode(admin).getSubject()));
        assertThat(audit.get("created_at")).isEqualTo(java.sql.Timestamp.from(clock.instant()));
        assertThat(audit.toString()).doesNotContain(admin, code("admin@unimagdalena.edu.co"));
    }

    @Test
    void auditFailureRollsBackTheParameterUpdate() throws Exception {
        String admin = administrator();
        jdbc.execute("create function reject_parameters_audit() returns trigger language plpgsql as $$ "
                + "begin if NEW.action = 'PARAMETERS_UPDATED' then raise exception 'test audit failure'; end if; return NEW; end $$");
        jdbc.execute("create trigger reject_parameters before insert on audit_log for each row execute function reject_parameters_audit()");
        try {
            error(send("PUT", "/admin/parameters", "{\"quota\":{\"weeklyHours\":5}}", admin), 500, "INTERNAL_ERROR");
            assertThat(parameters.current()).isEqualTo(new ParameterService.CurrentParameters(4, 3));
            assertThat(updateAuditCount()).isZero();
        } finally {
            jdbc.execute("drop trigger reject_parameters on audit_log");
            jdbc.execute("drop function reject_parameters_audit()");
        }
    }

    @Test
    void changingParametersPreservesExistingReservationsAndPublishesNewValues() throws Exception {
        String admin = administrator();
        UUID space = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        UUID user = UUID.fromString(decoder.decode(admin).getSubject());
        jdbc.update("insert into space(id,name,capacity,opening_time,closing_time,status) "
                + "values (?, 'Cancha', 10, '06:00', '22:00', 'ACTIVE')", space);
        jdbc.update("insert into reservation(id,user_id,space_id,start_at,end_at,status,created_at,"
                + "cancellation_threshold_minutes,checkin_opening_minutes) "
                + "values (?, ?, ?, '2026-11-12T14:00:00-05:00', '2026-11-12T16:00:00-05:00',"
                + "'CONFIRMED', '2026-11-11T07:00:00-05:00', 60, 10)", reservation, user, space);
        var before = jdbc.queryForMap("select * from reservation where id = ?", reservation);
        var response = send("PUT", "/admin/parameters",
                "{\"quota\":{\"weeklyHours\":1},\"sanctions\":{\"noShowsForSuspension\":1}}", admin);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForMap("select * from reservation where id = ?", reservation)).isEqualTo(before);
        assertThat(parameters.current()).isEqualTo(new ParameterService.CurrentParameters(1, 1));
        assertThat(body(send("GET", "/rules", null, admin))).isEqualTo(expectedRules(1, 1));
        assertThat(jdbc.queryForObject("select count(*) from monthly_suspension", Integer.class)).isZero();
    }

    @Test
    void concurrentPartialUpdatesPreserveBothAdministratorsChanges() throws Exception {
        String admin = administrator();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var quota = executor.submit(() -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return send("PUT", "/admin/parameters", "{\"quota\":{\"weeklyHours\":5}}", admin);
            });
            var sanctions = executor.submit(() -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return send("PUT", "/admin/parameters", "{\"sanctions\":{\"noShowsForSuspension\":2}}", admin);
            });
            start.countDown();
            assertThat(quota.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            assertThat(sanctions.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }
        assertThat(parameters.current()).isEqualTo(new ParameterService.CurrentParameters(5, 2));
        assertThat(updateAuditCount()).isEqualTo(2);
    }
}
