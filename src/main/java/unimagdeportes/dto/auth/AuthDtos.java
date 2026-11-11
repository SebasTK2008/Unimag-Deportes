package unimagdeportes.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import unimagdeportes.model.user.Role;

public final class AuthDtos {
    private AuthDtos() { }

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) { }
    public record VerifyRequest(@NotBlank @Email @Size(max = 254) String email,
                                @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
        @Override public String toString() { return "VerifyRequest[redacted]"; }
    }
    public record CodeRequest(@NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
        @Override public String toString() { return "CodeRequest[redacted]"; }
    }
    public record ProfileRequest(@NotBlank @Size(max = 200) String name) { }
    public record Message(String message) { }
    public record TokenResponse(String token, OffsetDateTime expiresAt, boolean needsName) {
        @Override public String toString() { return "TokenResponse[redacted]"; }
    }
    public record ReauthResponse(String token, OffsetDateTime expiresAt, OffsetDateTime reauthAt) {
        @Override public String toString() { return "ReauthResponse[redacted]"; }
    }
    public record Profile(UUID id, String email, String name, Role role,
                          OffsetDateTime suspendedUntil, boolean needsName) { }
}
