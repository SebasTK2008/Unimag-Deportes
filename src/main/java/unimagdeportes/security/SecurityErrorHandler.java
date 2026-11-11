package unimagdeportes.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        boolean expired = isExpired(exception);
        response.setHeader("WWW-Authenticate", "Bearer");
        write(response, 401, expired ? "TOKEN_EXPIRED" : "TOKEN_INVALID",
                expired ? "La sesión ha expirado." : "La autenticación no es válida.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        write(response, 403, "FORBIDDEN", "No tienes permiso para realizar esta acción.");
    }

    private boolean isExpired(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof JwtValidationException validation && validation.getErrors().stream()
                    .anyMatch(item -> "token_expired".equals(item.getErrorCode()))) {
                return true;
            }
        }
        return false;
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
