package unimagdeportes.controller.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import unimagdeportes.dto.auth.AuthDtos.CodeRequest;
import unimagdeportes.dto.auth.AuthDtos.EmailRequest;
import unimagdeportes.dto.auth.AuthDtos.Message;
import unimagdeportes.dto.auth.AuthDtos.Profile;
import unimagdeportes.dto.auth.AuthDtos.ProfileRequest;
import unimagdeportes.dto.auth.AuthDtos.ReauthResponse;
import unimagdeportes.dto.auth.AuthDtos.TokenResponse;
import unimagdeportes.dto.auth.AuthDtos.VerifyRequest;
import unimagdeportes.security.UserAuthentication;
import unimagdeportes.service.auth.AuthService;
import unimagdeportes.service.auth.LoginCodeService;

@RestController
public class AuthController {
    private final LoginCodeService codes;
    private final AuthService auth;

    public AuthController(LoginCodeService codes, AuthService auth) {
        this.codes = codes;
        this.auth = auth;
    }

    @PostMapping("/auth/code")
    ResponseEntity<Message> requestCode(@Valid @RequestBody EmailRequest body, HttpServletRequest request) {
        codes.request(body.email(), request.getRemoteAddr());
        return ResponseEntity.accepted().body(new Message(
                "Si el correo pertenece al dominio institucional, recibirás un código de acceso."));
    }

    @PostMapping("/auth/verify")
    TokenResponse verify(@Valid @RequestBody VerifyRequest body) {
        return auth.login(body.email(), body.code());
    }

    @GetMapping("/me")
    Profile profile(UserAuthentication authentication) {
        return auth.profile(authentication.currentUser());
    }

    @PutMapping("/me/profile")
    Profile completeProfile(UserAuthentication authentication, @Valid @RequestBody ProfileRequest body) {
        return auth.completeProfile(authentication.currentUser().getId(), body.name());
    }

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(UserAuthentication authentication) {
        auth.logout(authentication.currentUser().getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/reauth/code")
    ResponseEntity<Message> requestReauth(UserAuthentication authentication, HttpServletRequest request) {
        codes.request(authentication.currentUser().getEmail(), request.getRemoteAddr());
        return ResponseEntity.accepted().body(new Message("Se envió un código de reautenticación a tu correo institucional."));
    }

    @PostMapping("/auth/reauth/verify")
    ReauthResponse reauthenticate(UserAuthentication authentication, @Valid @RequestBody CodeRequest body) {
        var token = authentication.token();
        return auth.reauthenticate(authentication.currentUser(), token.getExpiresAt(), body.code());
    }
}
