package unimagdeportes.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ProfileCompletionFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getServletPath();
        boolean allowed = path.equals("/me") && request.getMethod().equals("GET")
                || path.equals("/me/profile") && request.getMethod().equals("PUT")
                || path.startsWith("/auth/");
        if (!allowed && authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("PROFILE_INCOMPLETE"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"code\":\"PROFILE_INCOMPLETE\",\"message\":\"Completa tu perfil.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
