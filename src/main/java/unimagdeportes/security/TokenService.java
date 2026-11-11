package unimagdeportes.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import unimagdeportes.model.user.AppUser;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }

    public String issue(AppUser user) {
        Instant now = clock.instant();
        return issue(user, now, now.plus(Duration.ofHours(24)), null);
    }

    public String issueReauthenticated(AppUser user, Instant expiresAt) {
        Instant now = clock.instant();
        return issue(user, now, expiresAt, now);
    }

    private String issue(AppUser user, Instant now, Instant expiresAt, Instant reauthAt) {
        var builder = JwtClaimsSet.builder().subject(user.getId().toString())
                .claim("email", user.getEmail()).claim("role", user.getRole().name())
                .issuedAt(now).expiresAt(expiresAt);
        if (reauthAt != null) {
            builder.claim("reauth_at", reauthAt.getEpochSecond());
        }
        JwtClaimsSet claims = builder.build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
