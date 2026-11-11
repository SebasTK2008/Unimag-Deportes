package unimagdeportes.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import unimagdeportes.model.user.AppUser;
import unimagdeportes.model.user.Role;

class TokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private final SecurityConfig config = new SecurityConfig();
    private final javax.crypto.spec.SecretKeySpec key = config.jwtKey("0123456789abcdef0123456789abcdef");

    @Test
    void issuesTokenWithDocumentedLifetime() {
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var user = new AppUser(UUID.randomUUID(), "a@unimagdalena.edu.co", Role.USER, NOW);
        String token = new TokenService(config.jwtEncoder(key), clock).issue(user);
        var decoded = config.jwtDecoder(key, clock).decode(token);
        assertThat(decoded.getSubject()).isEqualTo(user.getId().toString());
        assertThat(decoded.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void rejectsExpiredAndAlteredToken() {
        var issuedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        var user = new AppUser(UUID.randomUUID(), "a@unimagdalena.edu.co", Role.USER, NOW);
        String token = new TokenService(config.jwtEncoder(key), issuedClock).issue(user);
        var expiredClock = Clock.fixed(NOW.plus(Duration.ofHours(25)), ZoneOffset.UTC);
        assertThatThrownBy(() -> config.jwtDecoder(key, expiredClock).decode(token)).isInstanceOf(JwtException.class);
        var otherKey = config.jwtKey("abcdef0123456789abcdef0123456789");
        assertThatThrownBy(() -> config.jwtDecoder(otherKey, issuedClock).decode(token)).isInstanceOf(JwtException.class);
    }
}
