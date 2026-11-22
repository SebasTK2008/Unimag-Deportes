package unimagdeportes.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

class CorsConfigurationTest {
    private static final String LOCAL_ORIGIN = "http://localhost:5173";

    @ParameterizedTest
    @CsvSource({
            "GET, /me",
            "POST, /auth/code",
            "POST, /reservations",
            "PUT, /me/profile",
            "POST, /admin/spaces",
            "PUT, /admin/spaces/space-id/images/PREVIEW",
            "GET, /spaces/space-id/availability/stream"
    })
    void permitsLocalClientPreflightsWithoutCookies(String method, String path) throws Exception {
        var request = preflight(LOCAL_ORIGIN, method, path);
        request.addHeader("Access-Control-Request-Headers", "authorization,content-type");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter(LOCAL_ORIGIN).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(LOCAL_ORIGIN);
        assertThat(response.getHeader("Access-Control-Allow-Methods")).contains(method);
        assertThat(response.getHeader("Access-Control-Allow-Headers")).contains("authorization", "content-type");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isNull();
        assertThat(response.getHeader("Set-Cookie")).isNull();
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void acceptsEachExplicitOriginAndTrimsTheEnvironmentList() throws Exception {
        var cors = filter(" " + LOCAL_ORIGIN + ", , https://deportes.example ");
        for (String origin : new String[]{LOCAL_ORIGIN, "https://deportes.example"}) {
            var response = new MockHttpServletResponse();

            cors.doFilter(preflight(origin, "GET", "/me"), response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(origin);
        }
    }

    @Test
    void rejectsUnknownOriginsIncludingADifferentLocalPort() throws Exception {
        for (String origin : new String[]{"https://evil.example", "http://localhost:5174", "http://127.0.0.1:5173"}) {
            var response = new MockHttpServletResponse();
            var chain = new MockFilterChain();

            filter(LOCAL_ORIGIN).doFilter(preflight(origin, "GET", "/me"), response, chain);

            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
            assertThat(chain.getRequest()).isNull();
        }
    }

    @Test
    void rejectsCrossOriginRequestsWhenNoOriginIsConfigured() throws Exception {
        var response = new MockHttpServletResponse();

        filter("").doFilter(preflight(LOCAL_ORIGIN, "GET", "/me"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void rejectsUnsupportedMethodsAndHeaders() throws Exception {
        var methodResponse = new MockHttpServletResponse();
        var headerResponse = new MockHttpServletResponse();
        var request = preflight(LOCAL_ORIGIN, "GET", "/me");
        request.addHeader("Access-Control-Request-Headers", "x-untrusted-header");

        filter(LOCAL_ORIGIN).doFilter(preflight(LOCAL_ORIGIN, "DELETE", "/me"),
                methodResponse, new MockFilterChain());
        filter(LOCAL_ORIGIN).doFilter(request, headerResponse, new MockFilterChain());

        assertThat(methodResponse.getStatus()).isEqualTo(403);
        assertThat(headerResponse.getStatus()).isEqualTo(403);
    }

    @Test
    void addsCorsHeadersToActualRequestsWithoutReplacingAuthentication() throws Exception {
        var request = new MockHttpServletRequest("GET", "/me");
        request.addHeader("Origin", LOCAL_ORIGIN);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter(LOCAL_ORIGIN).doFilter(request, response, chain);

        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(LOCAL_ORIGIN);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void allowsRequestsWithoutOriginToContinueToTheSecurityChain() throws Exception {
        var request = new MockHttpServletRequest("GET", "/me");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter("").doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
    }

    private CorsFilter filter(String origins) {
        return new CorsFilter(new SecurityConfig().corsConfigurationSource(origins));
    }

    private MockHttpServletRequest preflight(String origin, String method, String path) {
        var request = new MockHttpServletRequest("OPTIONS", path);
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", method);
        return request;
    }
}
