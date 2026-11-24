package unimagdeportes.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import unimagdeportes.service.audit.AuditService;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

public class AuthAuditFilter extends OncePerRequestFilter {
    private final AuditService audit;

    public AuthAuditFilter(AuditService audit) { this.audit = audit; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        boolean codeRequest = path.equals("/auth/code") || path.equals("/auth/reauth/code");
        boolean verification = path.equals("/auth/verify") || path.equals("/auth/reauth/verify");
        if (!request.getMethod().equals("POST") || (!codeRequest && !verification)) {
            chain.doFilter(request, response);
            return;
        }
        // Commit the audit entry before releasing the HTTP response to the caller.
        var buffered = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, buffered);
        if (path.equals("/auth/code") || path.equals("/auth/reauth/code")) {
            audit.record("CODE_REQUESTED", "LOGIN_CODE", null, null);
        } else if (verification && buffered.getStatus() >= 400) {
            audit.record("LOGIN_FAILED", "LOGIN_CODE", null, null);
        }
        buffered.copyBodyToResponse();
    }
}
