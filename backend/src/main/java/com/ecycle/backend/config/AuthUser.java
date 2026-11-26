package com.ecycle.backend.config;

import com.ecycle.shared.Enums.Role;
import java.util.Set;

/** Authenticated principal placed in the SecurityContext by JwtAuthFilter. */
public record AuthUser(Long id, String email, Set<Role> roles) {
    public boolean has(Role r) { return roles.contains(r); }
    public boolean isStaff() { return roles.contains(Role.RESOLVER); }
}
