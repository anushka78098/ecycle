package com.ecycle.backend.config;

import com.ecycle.backend.repository.TokenRevocationRepository;
import com.ecycle.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtService jwt; private final UserRepository users; private final TokenRevocationRepository revoked; private final AppProps props;

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    /** The API authenticates via JWT only; this prevents Spring Boot from creating its default in-memory user. */
    @Bean UserDetailsService noUserDetails() { return u -> { throw new UsernameNotFoundException(u); }; }

    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http
            // Stateless bearer-token API for a desktop client: no cookies/sessions, so CSRF does not apply.
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/logout").authenticated()
                .requestMatchers("/api/auth/**", "/api/health", "/error", "/api/public/**").permitAll()
                .requestMatchers("/ws/**").permitAll()   // the STOMP CONNECT frame is authenticated by WebSocketConfig (JWT)
                .requestMatchers(HttpMethod.GET, "/api/categories").permitAll()
                .requestMatchers("/api/admin/**").hasRole("RESOLVER")
                .requestMatchers("/api/resolver/**").hasRole("RESOLVER")
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> { res.setStatus(401); res.setContentType("application/json");
                    res.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"); })
                .accessDeniedHandler((req, res, ex) -> { res.setStatus(403); res.setContentType("application/json");
                    res.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"You are not allowed to perform this action\"}"); }))
            .addFilterBefore(new RateLimitFilter(props.getRateLimitPerMinute()), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtAuthFilter(jwt, users, revoked), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
