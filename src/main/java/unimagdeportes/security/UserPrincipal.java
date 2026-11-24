package unimagdeportes.security;

import java.util.UUID;
import unimagdeportes.model.user.Role;

public record UserPrincipal(UUID id, String email, Role role) { }
