package unimagdeportes.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ProfileCompletionFilterTest {
    private final ProfileCompletionFilter filter = new ProfileCompletionFilter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void incompleteProfileIsRejectedFromBusinessRoute() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", "token", List.of(new SimpleGrantedAuthority("PROFILE_INCOMPLETE"))));
        var request = new MockHttpServletRequest("GET", "/spaces");
        request.setServletPath("/spaces");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("PROFILE_INCOMPLETE");
    }

    @Test
    void incompleteProfileMayUpdateProfile() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", "token", List.of(new SimpleGrantedAuthority("PROFILE_INCOMPLETE"))));
        var request = new MockHttpServletRequest("PUT", "/me/profile");
        request.setServletPath("/me/profile");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
