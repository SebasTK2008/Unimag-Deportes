package unimagdeportes.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import unimagdeportes.service.policy.ReauthPolicy;

public class ReauthenticationFilter extends OncePerRequestFilter {
    private final Clock clock;
    private final ReauthPolicy policy = new ReauthPolicy();

    public ReauthenticationFilter(Clock clock) { this.clock = clock; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean sensitive = request.getMethod().equals("POST") && request.getServletPath().equals("/admin/blocks")
                || request.getMethod().equals("PUT") && request.getServletPath().equals("/admin/parameters");
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (sensitive && authentication instanceof UserAuthentication userAuthentication
                && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            Object claim = userAuthentication.token().getClaim("reauth_at");
            Instant at = null;
            if (claim instanceof Number number) {
                long seconds = number.longValue();
                if (seconds >= 0 && seconds <= clock.instant().getEpochSecond()) {
                    at = Instant.ofEpochSecond(seconds);
                }
            }
            if (!policy.isValid(at, clock.instant())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                String message = request.getServletPath().equals("/admin/parameters")
                        ? "Debes reautenticarte para cambiar las reglas." : "Debes reautenticarte.";
                response.getWriter().write("{\"code\":\"REAUTH_REQUIRED\",\"message\":\"" + message + "\",\"details\":{}}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
