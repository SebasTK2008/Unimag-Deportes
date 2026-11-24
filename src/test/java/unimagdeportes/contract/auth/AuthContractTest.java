package unimagdeportes.contract.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import unimagdeportes.support.AuthTestSupport;

class AuthContractTest extends AuthTestSupport {
    @Test
    void firstLoginProfileAndLogoutFollowTheContract() throws Exception {
        String email = "first@unimagdalena.edu.co";
        var request = requestCode(email);
        assertThat(request.statusCode()).isEqualTo(202);
        assertThat(body(request).get("message").asString()).contains("recibirás un código");
        var verified = verify(email, code(email));
        assertThat(verified.statusCode()).isEqualTo(200);
        assertThat(body(verified).get("needsName").asBoolean()).isTrue();
        assertThat(OffsetDateTime.parse(body(verified).get("expiresAt").asString()).getOffset().getTotalSeconds())
                .isEqualTo(-18000);
        String token = body(verified).get("token").asString();
        var claims = decoder.decode(token);
        assertThat(Duration.between(claims.getIssuedAt(), claims.getExpiresAt())).isEqualTo(Duration.ofHours(24));
        assertThat(claims.getClaimAsString("email")).isEqualTo(email);
        assertThat(claims.getClaimAsString("role")).isEqualTo("USER");
        assertThat(claims.hasClaim("reauth_at")).isFalse();
        var me = send("GET", "/me", null, token);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(body(me).get("name").isNull()).isTrue();
        assertThat(body(me).get("suspendedUntil").isNull()).isTrue();
        error(send("PUT", "/me/profile", "{\"name\":\" \"}", token), 400, "VALIDATION_ERROR");
        var profile = send("PUT", "/me/profile", "{\"name\":\"  Juan Pérez  \"}", token);
        assertThat(body(profile).get("name").asString()).isEqualTo("Juan Pérez");
        assertThat(body(profile).get("needsName").asBoolean()).isFalse();
        var logout = send("POST", "/auth/logout", null, token);
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(logout.body()).isEmpty();
        assertThat(send("GET", "/me", null, token).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'LOGOUT'", Integer.class)).isEqualTo(1);
    }

    @Test
    void validatesBodiesAndInstitutionalDomainWithoutSendingMail() throws Exception {
        for (String email : new String[]{"x@example.com", "x@unimagdalena.edu.co.evil.com", "x@sub.unimagdalena.edu.co"}) {
            error(requestCode(email), 400, "INVALID_EMAIL_DOMAIN");
        }
        error(send("POST", "/auth/code", "{}", null), 400, "VALIDATION_ERROR");
        error(send("POST", "/auth/code", "{", null), 400, "VALIDATION_ERROR");
        error(send("POST", "/auth/verify", "{\"email\":\"x@unimagdalena.edu.co\",\"code\":\"123\"}", null),
                400, "VALIDATION_ERROR");
        assertThat(mail.bodies).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from login_code", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'CODE_REQUESTED'", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'LOGIN_FAILED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void administratorReauthenticatesWithoutExtendingTheSession() throws Exception {
        String email = "admin@unimagdalena.edu.co";
        String token = completeLogin(email);
        var original = decoder.decode(token);
        assertThat(original.getClaimAsString("role")).isEqualTo("ADMIN");
        error(send("POST", "/admin/blocks", "{}", token), 403, "REAUTH_REQUIRED");
        clock.advance(Duration.ofSeconds(60));
        var request = send("POST", "/auth/reauth/code", null, token);
        assertThat(request.statusCode()).isEqualTo(202);
        assertThat(body(request).get("message").asString()).contains("reautenticación");
        error(send("POST", "/auth/reauth/verify", "{\"code\":\"invalid\"}", token), 400, "VALIDATION_ERROR");
        var reauth = send("POST", "/auth/reauth/verify", "{\"code\":\"" + code(email) + "\"}", token);
        assertThat(reauth.statusCode()).isEqualTo(200);
        String newToken = body(reauth).get("token").asString();
        assertThat(decoder.decode(newToken).getExpiresAt()).isEqualTo(original.getExpiresAt());
        assertThat(body(reauth).get("reauthAt").asString()).isEqualTo("2026-11-11T07:01:00-05:00");
        error(send("POST", "/auth/reauth/verify", "{\"code\":\"" + code(email) + "\"}", token), 400, "CODE_INVALIDATED");
        clock.advance(Duration.ofMinutes(10));
        error(send("PUT", "/admin/parameters", "{}", newToken), 403, "REAUTH_REQUIRED");
    }

    @Test
    void reauthenticationRequiresCurrentAdministratorRole() throws Exception {
        String token = completeLogin("user@unimagdalena.edu.co");
        error(send("POST", "/auth/reauth/code", null, token), 403, "FORBIDDEN");
        error(send("POST", "/auth/reauth/verify", "{\"code\":\"123456\"}", token), 403, "FORBIDDEN");
        for (String path : new String[]{"/me", "/auth/logout", "/auth/reauth/code", "/auth/reauth/verify"}) {
            error(send(path.equals("/me") ? "GET" : "POST", path, null, null), 401, "TOKEN_INVALID");
        }
    }
}
