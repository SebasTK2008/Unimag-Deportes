package unimagdeportes.controller.dev;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import unimagdeportes.security.TokenService;
import unimagdeportes.service.user.UserService;

@RestController
@RequestMapping("/dev")
@Profile("dev")
public class DevLoginController {
    private final UserService users;
    private final TokenService tokens;

    public DevLoginController(UserService users, TokenService tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return new LoginResponse(tokens.issue(users.resolveDevUser(request.email(), request.name())));
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String name) { }
    public record LoginResponse(String token) { }
}
