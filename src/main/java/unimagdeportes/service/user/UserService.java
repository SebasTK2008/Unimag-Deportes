package unimagdeportes.service.user;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import unimagdeportes.exception.BusinessException;
import unimagdeportes.model.user.AppUser;
import unimagdeportes.model.user.Role;
import unimagdeportes.repository.user.AppUserRepository;

@Service
public class UserService {
    private final AppUserRepository users;
    private final Clock clock;
    private final String adminEmails;

    public UserService(AppUserRepository users, Clock clock,
            @Value("${app.auth.admin-emails:}") String adminEmails) {
        this.users = users;
        this.clock = clock;
        this.adminEmails = adminEmails;
    }

    @Transactional
    public AppUser resolveOrCreate(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[a-z0-9._%+-]+@unimagdalena\\.edu\\.co$")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL_DOMAIN", "Correo institucional inválido.");
        }
        return users.findByEmail(normalized).orElseGet(() -> {
            boolean admin = java.util.Arrays.stream(adminEmails.split(","))
                    .anyMatch(value -> value.trim().equalsIgnoreCase(normalized));
            return users.save(new AppUser(UUID.randomUUID(), normalized,
                    admin ? Role.ADMIN : Role.USER, clock.instant()));
        });
    }

    @Transactional(readOnly = true)
    public AppUser findById(UUID id) {
        return users.findById(id).orElseThrow();
    }

    @Transactional
    public AppUser completeProfile(UUID id, String name) {
        AppUser user = users.findById(id).orElseThrow();
        user.completeProfile(name.trim());
        return user;
    }

    @Transactional
    public AppUser resolveDevUser(String email, String name) {
        AppUser user = resolveOrCreate(email);
        if (user.getName() == null || user.getName().isBlank()) {
            user.completeProfile(name.trim());
        }
        return user;
    }
}
