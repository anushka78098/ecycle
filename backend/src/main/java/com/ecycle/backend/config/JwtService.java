package com.ecycle.backend.config;

import com.ecycle.backend.entity.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    public record Issued(String token, String jti, Instant expiresAt) {}
    private final SecretKey key;
    private final long expirySeconds;

    public JwtService(AppProps p) {
        String secret = p.getJwtSecret();
        if (secret == null || secret.length() < 32) {
            if (!p.isDevMode()) throw new IllegalStateException("JWT_SECRET must be set (>= 32 characters) outside dev mode");
            byte[] b = new byte[48]; new SecureRandom().nextBytes(b);
            secret = HexFormat.of().formatHex(b);
            log.warn("DEV MODE: using an ephemeral JWT secret; all sessions end when the server restarts");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirySeconds = p.getJwtExpiryMinutes() * 60;
    }
    public long expirySeconds() { return expirySeconds; }
    public Issued issue(AppUser u) {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now(), exp = now.plusSeconds(expirySeconds);
        String t = Jwts.builder().id(jti).subject(String.valueOf(u.getId())).claim("email", u.getEmail())
                .issuedAt(Date.from(now)).expiration(Date.from(exp)).signWith(key).compact();
        return new Issued(t, jti, exp);
    }
    public Claims parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
}
