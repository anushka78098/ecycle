package com.ecycle.frontend.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.Set;

/** In-memory session of the signed-in user. The token is never written to disk. */
public final class Session {
    private Session() {}
    public static String token; public static Long userId; public static String name; public static String email;
    public static final Set<String> roles = new LinkedHashSet<>();
    public static String activeRole;

    public static void start(JsonNode auth) {
        token = auth.path("token").asText(); userId = auth.path("userId").asLong(); name = auth.path("fullName").asText(); email = auth.path("email").asText();
        roles.clear(); auth.path("roles").forEach(r -> roles.add(r.asText()));
        activeRole = roles.stream().findFirst().orElse(null);
    }
    public static void clear() { token = null; userId = null; name = null; email = null; roles.clear(); activeRole = null; }
    public static boolean active() { return token != null; }
    public static boolean is(String role) { return role.equals(activeRole); }
}
