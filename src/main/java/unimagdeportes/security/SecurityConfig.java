package unimagdeportes.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import unimagdeportes.model.user.AppUser;
import unimagdeportes.service.user.UserService;

@Configuration
public class SecurityConfig {
    @Bean
    SecretKeySpec jwtKey(@Value("${app.auth.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("app.auth.jwt.secret debe tener al menos 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKeySpec key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKeySpec key, Clock clock) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, jwt -> {
            if (jwt.getSubject() == null || jwt.getExpiresAt() == null || jwt.getIssuedAt() == null) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
            }
            return OAuth2TokenValidatorResult.success();
        }, jwt -> {
            if (jwt.getExpiresAt() != null && !clock.instant().isBefore(jwt.getExpiresAt())) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("token_expired"));
            }
            return OAuth2TokenValidatorResult.success();
        }));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserService users) throws Exception {
        var securityErrors = new SecurityErrorHandler();
        return http.csrf(csrf -> csrf.disable()).cors(cors -> { })
                .headers(headers -> headers.contentSecurityPolicy(csp ->
                        csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/dev/login", "/auth/code", "/auth/verify").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(securityErrors)
                        .accessDeniedHandler(securityErrors))
                .oauth2ResourceServer(resource -> resource.authenticationEntryPoint(securityErrors)
                        .accessDeniedHandler(securityErrors).jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                    AppUser user;
                    try {
                        user = users.findById(UUID.fromString(token.getSubject()));
                    } catch (IllegalArgumentException | java.util.NoSuchElementException ex) {
                        throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
                                new org.springframework.security.oauth2.core.OAuth2Error("invalid_token"), ex);
                    }
                    UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail(), user.getRole());
                    var authorities = new ArrayList<SimpleGrantedAuthority>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                    if (user.getName() == null || user.getName().isBlank()) {
                        authorities.add(new SimpleGrantedAuthority("PROFILE_INCOMPLETE"));
                    }
                    return (AbstractAuthenticationToken) new UsernamePasswordAuthenticationToken(principal, token,
                            authorities);
                })))
                .addFilterAfter(new ProfileCompletionFilter(),
                        org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
