package unimagdeportes.service.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.model.user.AppUser;
import unimagdeportes.security.TokenService;
import unimagdeportes.service.audit.AuditService;
import unimagdeportes.service.user.UserService;
import unimagdeportes.dto.auth.AuthDtos.Profile;
import unimagdeportes.dto.auth.AuthDtos.ReauthResponse;
import unimagdeportes.dto.auth.AuthDtos.TokenResponse;
import unimagdeportes.mapper.auth.AuthMapper;
import unimagdeportes.model.user.Role;

@Service
public class AuthService {
    private final LoginCodeService codes;
    private final UserService users;
    private final TokenService tokens;
    private final AuditService audit;
    private final JwtDecoder decoder;
    private final AuthMapper mapper;
    private final Clock clock;

    public AuthService(LoginCodeService codes, UserService users, TokenService tokens, AuditService audit,
            JwtDecoder decoder, AuthMapper mapper, Clock clock) {
        this.codes = codes;
        this.users = users;
        this.tokens = tokens;
        this.audit = audit;
        this.decoder = decoder;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse login(String email, String code) {
        codes.verify(email, code);
        AppUser user = users.resolveOrCreate(email);
        String token = tokens.issue(user);
        audit.record("LOGIN", "APP_USER", user.getId(), user.getId());
        return new TokenResponse(token, mapper.offset(decoder.decode(token).getExpiresAt()), mapper.needsName(user));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ReauthResponse reauthenticate(AppUser user, Instant expiresAt, String code) {
        if (user.getRole() != Role.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Solo un Administrador puede reautenticarse.");
        }
        if (!clock.instant().isBefore(expiresAt)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "La sesión ha expirado.");
        }
        codes.verify(user.getEmail(), code);
        String token = tokens.issueReauthenticated(user, expiresAt);
        var jwt = decoder.decode(token);
        audit.record("LOGIN", "APP_USER", user.getId(), user.getId());
        return new ReauthResponse(token, mapper.offset(expiresAt),
                mapper.offset(Instant.ofEpochSecond(((Number) jwt.getClaim("reauth_at")).longValue())));
    }

    public Profile profile(AppUser user) { return mapper.profile(user, clock); }

    public Profile completeProfile(UUID id, String name) { return profile(users.completeProfile(id, name)); }

    public void logout(UUID id) { audit.record("LOGOUT", "APP_USER", id, id); }
}
