package com.ecycle.backend.config;

import com.ecycle.backend.repository.TokenRevocationRepository;
import com.ecycle.backend.repository.UserRepository;
import com.ecycle.shared.Enums.AccountStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Validates the bearer token, rejects revoked tokens and suspended accounts, and loads roles from the database on every request. */
public class JwtAuthFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private final JwtService jwt; private final UserRepository users; private final TokenRevocationRepository revoked;
    public JwtAuthFilter(JwtService jwt, UserRepository users, TokenRevocationRepository revoked) { this.jwt = jwt; this.users = users; this.revoked = revoked; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            try {
                Claims c = jwt.parse(h.substring(7));
                if (!revoked.existsByJti(c.getId())) {
                    users.findById(Long.valueOf(c.getSubject())).filter(u -> u.getStatus() == AccountStatus.ACTIVE).ifPresent(u -> {
                        AuthUser au = new AuthUser(u.getId(), u.getEmail(), u.roleSet());
                        var auth = new UsernamePasswordAuthenticationToken(au, null,
                                au.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
                }
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("Rejected bearer token: {}", e.getMessage());
            }
        }
        chain.doFilter(req, res);
    }
}
