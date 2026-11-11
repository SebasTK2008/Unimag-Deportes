package unimagdeportes.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.mail.MailSender;
import unimagdeportes.model.auth.LoginCode;
import unimagdeportes.repository.auth.LoginCodeRepository;
import unimagdeportes.service.policy.LoginCodePolicy;

@Service
public class LoginCodeService {
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final int MAX_REQUESTS_PER_EMAIL = 5;
    private static final int MAX_REQUESTS_PER_IP = 30;
    private final LoginCodeRepository codes;
    private final JdbcTemplate jdbc;
    private final MailSender mail;
    private final Clock clock;
    private final LoginCodePolicy policy;
    private final Duration validity;
    private final int maxAttempts;

    public LoginCodeService(LoginCodeRepository codes, JdbcTemplate jdbc, MailSender mail,
            Clock clock, @Value("${app.auth.code.hmac-secret}") String secret,
            @Value("${app.auth.code.validity:PT15M}") Duration validity,
            @Value("${app.auth.code.max-attempts:5}") int maxAttempts) {
        if (validity.isNegative() || validity.isZero() || maxAttempts < 1) {
            throw new IllegalArgumentException("Configuración de códigos inválida");
        }
        this.codes = codes;
        this.jdbc = jdbc;
        this.mail = mail;
        this.clock = clock;
        this.policy = new LoginCodePolicy(new SecureRandom(), secret.getBytes(StandardCharsets.UTF_8));
        this.validity = validity;
        this.maxAttempts = maxAttempts;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void request(String email, String ip) {
        String normalized = normalize(email);
        lock("email:" + normalized);
        lock("ip:" + ip);
        Instant now = clock.instant();
        List<LoginCode> recent = codes.findByEmailAndCreatedAtAfterOrderByCreatedAtAsc(normalized, now.minus(WINDOW));
        if (!recent.isEmpty()) {
            Instant next = recent.getLast().getCreatedAt().plusSeconds(60);
            if (now.isBefore(next)) {
                throw limited("RESEND_TOO_SOON", next, now);
            }
        }
        if (recent.size() >= MAX_REQUESTS_PER_EMAIL) {
            throw limited("CODE_REQUEST_LIMIT", recent.get(recent.size() - MAX_REQUESTS_PER_EMAIL).getCreatedAt().plus(WINDOW), now);
        }
        List<Instant> ipRequests = jdbc.query("select created_at from login_code_request_ip "
                        + "where ip_address = ? and created_at > ? order by created_at",
                (rs, row) -> rs.getTimestamp(1).toInstant(), ip, java.sql.Timestamp.from(now.minus(WINDOW)));
        if (ipRequests.size() >= MAX_REQUESTS_PER_IP) {
            throw limited("IP_CODE_REQUEST_LIMIT", ipRequests.get(ipRequests.size() - MAX_REQUESTS_PER_IP).plus(WINDOW), now);
        }
        String code = policy.generate();
        codes.findFirstByEmailOrderByCreatedAtDesc(normalized).ifPresent(previous -> previous.markUsed(now));
        LoginCode loginCode = codes.save(new LoginCode(UUID.randomUUID(), normalized, policy.hash(normalized, code),
                now.plus(validity), now));
        jdbc.update("insert into login_code_request_ip (id, ip_address, created_at) values (?, ?, ?)",
                UUID.randomUUID(), ip, java.sql.Timestamp.from(now));
        try {
            mail.send(normalized, "Código de acceso", "Tu código de acceso es " + code
                    + ". Vence en " + validity.toMinutes() + " minutos. No lo compartas.");
        } catch (MailException ex) {
            loginCode.markUsed(now);
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_DELIVERY_FAILED",
                    "No fue posible enviar el código. Inténtalo nuevamente más tarde.", ex);
        }
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void verify(String email, String code) {
        String normalized = normalize(email);
        lock("email:" + normalized);
        Instant now = clock.instant();
        LoginCode current = codes.findFirstByEmailOrderByCreatedAtDesc(normalized).orElse(null);
        if (current == null || current.getUsedAt() != null) {
            throw invalid("CODE_INVALIDATED", "El código ya no es válido.", Map.of());
        }
        if (!now.isBefore(current.getExpiresAt())) {
            throw invalid("CODE_EXPIRED", "El código venció. Solicita uno nuevo.", Map.of());
        }
        if (!policy.matches(normalized, code, current.getCodeHash())) {
            current.recordFailedAttempt();
            int remaining = maxAttempts - current.getAttempts();
            if (remaining <= 0) {
                current.markUsed(now);
                throw invalid("TOO_MANY_ATTEMPTS", "Superaste los intentos permitidos.", Map.of());
            }
            throw invalid("INVALID_CODE", "El código no es correcto.", Map.of("attemptsRemaining", remaining));
        }
        current.markUsed(now);
    }

    private BusinessException invalid(String error, String message, Map<String, ?> details) {
        return new BusinessException(HttpStatus.BAD_REQUEST, error, message, details);
    }

    private BusinessException limited(String code, Instant next, Instant now) {
        long seconds = Math.max(1, (Duration.between(now, next).toMillis() + 999) / 1000);
        return new BusinessException(HttpStatus.TOO_MANY_REQUESTS, code,
                "Debes esperar antes de solicitar otro código.", Map.of("retryAfterSeconds", seconds));
    }

    private void lock(String value) {
        // The email may not have a user row yet; transaction-scoped locks also serialize first login.
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null, value);
    }

    public static String normalize(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[a-z0-9._%+-]+@unimagdalena\\.edu\\.co$")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL_DOMAIN",
                    "El correo debe pertenecer al dominio institucional.", Map.of("field", "email"));
        }
        return normalized;
    }
}
