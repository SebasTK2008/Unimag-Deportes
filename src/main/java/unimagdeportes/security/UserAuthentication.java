package unimagdeportes.security;

import java.util.Collection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import unimagdeportes.model.user.AppUser;

public class UserAuthentication extends UsernamePasswordAuthenticationToken {
    private final AppUser currentUser;
    private final Jwt token;

    public UserAuthentication(AppUser user, Jwt token, Collection<? extends GrantedAuthority> authorities) {
        super(new UserPrincipal(user.getId(), user.getEmail(), user.getRole()), null, authorities);
        currentUser = user;
        this.token = token;
    }

    public AppUser currentUser() { return currentUser; }
    public Jwt token() { return token; }
}
