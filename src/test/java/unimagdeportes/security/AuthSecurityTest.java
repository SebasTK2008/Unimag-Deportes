package unimagdeportes.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import unimagdeportes.support.AuthTestSupport;

@ExtendWith(OutputCaptureExtension.class)
class AuthSecurityTest extends AuthTestSupport {
    @Autowired jakarta.persistence.EntityManagerFactory entities;

    @Test
    void profileUsesOnlyOneUserReadPerRequest() throws Exception {
        String token = completeLogin("query@unimagdalena.edu.co");
        var statistics = entities.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            assertThat(send("GET", "/me", null, token).statusCode()).isEqualTo(200);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }
    @Test
    void rejectsExpiredTokensAtExactlyTwentyFourHoursOnEveryProtectedAuthEndpoint() throws Exception {
        String token = completeLogin("expiry@unimagdalena.edu.co");
        clock.advance(Duration.ofHours(24));
        for (String path : new String[]{"/me", "/me/profile", "/auth/logout", "/auth/reauth/code", "/auth/reauth/verify"}) {
            String method = path.equals("/me") ? "GET" : path.equals("/me/profile") ? "PUT" : "POST";
            error(send(method, path, "{}", token), 401, "TOKEN_EXPIRED");
        }
    }

    @Test
    void rejectsTamperingUnsignedAndWrongAlgorithmTokensAndUrlTokens() throws Exception {
        String token = completeLogin("tamper@unimagdalena.edu.co");
        String[] parts = token.split("\\.");
        String altered = parts[0] + "." + base64("{\"sub\":\"attacker\"}") + "." + parts[2];
        String unsigned = base64("{\"alg\":\"none\"}") + "." + parts[1] + ".";
        String wrongAlg = base64("{\"alg\":\"HS512\"}") + "." + parts[1] + "." + parts[2];
        for (String invalid : new String[]{altered, unsigned, wrongAlg, "not-a-jwt"}) {
            error(send("GET", "/me", null, invalid), 401, "TOKEN_INVALID");
        }
        error(send("GET", "/me?access_token=" + token, null, null), 401, "TOKEN_INVALID");
    }

    @Test
    void readsTheCurrentRoleInsteadOfAuthorizingWithTokenClaims() throws Exception {
        String email = "role@unimagdalena.edu.co";
        String token = completeLogin(email);
        var decoded = decoder.decode(token);
        var forgedRole = JwtClaimsSet.builder().claims(claims -> claims.putAll(decoded.getClaims()))
                .claim("role", "ADMIN").build();
        String signed = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), forgedRole))
                .getTokenValue();
        error(send("POST", "/auth/reauth/code", null, signed), 403, "FORBIDDEN");
        jdbc.update("update app_user set role = 'ADMIN' where email = ?", email);
        assertThat(body(send("GET", "/me", null, token)).get("role").asString()).isEqualTo("ADMIN");
        error(send("POST", "/admin/blocks", "{}", token), 403, "REAUTH_REQUIRED");
        jdbc.update("update app_user set role = 'USER' where email = ?", email);
        error(send("POST", "/admin/blocks", "{}", token), 403, "FORBIDDEN");
    }

    @Test
    void suspensionIsFreshAndDoesNotPreventLoginOrReadingTheProfile() throws Exception {
        String email = "suspended@unimagdalena.edu.co";
        String token = completeLogin(email);
        jdbc.update("update app_user set suspended_until = ? where email = ?",
                java.sql.Timestamp.from(START.plus(Duration.ofDays(1))), email);
        var me = send("GET", "/me", null, token);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(body(me).get("suspendedUntil").asString()).isEqualTo("2026-11-12T07:00:00-05:00");
        clock.advance(Duration.ofMinutes(1));
        String newToken = login(email);
        assertThat(send("GET", "/me", null, newToken).statusCode()).isEqualTo(200);
        jdbc.update("update app_user set suspended_until = null where email = ?", email);
        assertThat(body(send("GET", "/me", null, token)).get("suspendedUntil").isNull()).isTrue();
    }

    @Test
    void deletedUsersCannotUseExistingTokens() throws Exception {
        String email = "deleted@unimagdalena.edu.co";
        String token = completeLogin(email);
        jdbc.update("delete from audit_log");
        jdbc.update("delete from app_user where email = ?", email);
        error(send("GET", "/me", null, token), 401, "TOKEN_INVALID");
    }

    @Test
    void incompleteProfilesAreRestrictedUntilTheNameIsSaved() throws Exception {
        String token = login("incomplete@unimagdalena.edu.co");
        error(send("GET", "/test/protected", null, token), 403, "PROFILE_INCOMPLETE");
        assertThat(send("GET", "/me", null, token).statusCode()).isEqualTo(200);
        assertThat(send("PUT", "/me/profile", "{\"name\":\"Persona\"}", token).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/test/protected", null, token).statusCode()).isEqualTo(204);
    }

    @Test
    void reauthenticationIsValidOnlyForTenMinutesAndDoesNotInvalidateOtherSessions() throws Exception {
        String email = "admin@unimagdalena.edu.co";
        String original = completeLogin(email);
        clock.advance(Duration.ofSeconds(60));
        assertThat(send("POST", "/auth/reauth/code", null, original).statusCode()).isEqualTo(202);
        var verified = send("POST", "/auth/reauth/verify", json.writeValueAsString(Map.of("code", code(email))), original);
        assertThat(verified.statusCode()).isEqualTo(200);
        String reauthenticated = body(verified).get("token").asString();
        for (String path : new String[]{"/admin/blocks", "/admin/parameters"}) {
            assertThat(send(path.endsWith("blocks") ? "POST" : "PUT", path, "{}", reauthenticated).statusCode()).isEqualTo(204);
        }
        assertThat(send("GET", "/me", null, original).statusCode()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(10));
        error(send("POST", "/admin/blocks", "{}", reauthenticated), 403, "REAUTH_REQUIRED");
        jdbc.update("update app_user set role = 'USER' where email = ?", email);
        error(send("POST", "/admin/blocks", "{}", reauthenticated), 403, "FORBIDDEN");
    }

    @Test
    void corsIsExplicitAndTheApiSetsCsp() throws Exception {
        var allowed = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/me"))
                .header("Origin", "https://client.example").header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization").method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).contains("https://client.example");
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
        var response = send("GET", "/me", null, null);
        assertThat(response.headers().firstValue("Content-Security-Policy")).contains("default-src 'none'; frame-ancestors 'none'");
        var rejected = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/me"))
                .header("Origin", "https://evil.example").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(rejected.statusCode()).isEqualTo(403);
    }

    @Test
    void codesAndTokensDoNotLeakIntoDatabaseAuditOrApplicationLogs(CapturedOutput output) throws Exception {
        String email = "secrets@unimagdalena.edu.co";
        requestCode(email);
        String code = code(email);
        var response = verify(email, code);
        String token = body(response).get("token").asString();
        send("POST", "/auth/logout", null, token);
        String stored = jdbc.queryForObject("select code_hash from login_code where email = ?", String.class, email);
        assertThat(stored).matches("[a-f0-9]{64}").isNotEqualTo(code);
        String audit = jdbc.queryForObject("select json_agg(a)::text from audit_log a", String.class);
        assertThat(audit).doesNotContain(code, token);
        assertThat(output.getAll()).doesNotContain(code, token);
        assertThat(mail.bodies.get(email)).contains("15 minutos", "No lo compartas");
    }

    private String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
