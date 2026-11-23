package com.ecycle.backend.controller;

import com.ecycle.backend.config.Sec;
import com.ecycle.backend.service.AuthService;
import com.ecycle.shared.Dtos.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class AuthController {
    private final AuthService auth; private final com.ecycle.backend.service.GoogleTokenVerifier google;

    @GetMapping("/health")
    public Map<String, Object> health() { return Map.of("status", "UP", "service", "ecycle-backend", "time", Instant.now().toString()); }

    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest r) { return auth.register(r); }
    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r) { return auth.login(r); }
    @PostMapping("/auth/google")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest r) { return auth.googleLogin(google.verify(r.idToken()), r.role()); }
    @GetMapping("/auth/google/config")
    public Map<String, Object> googleConfig() { return Map.of("enabled", google.enabled()); }
    @PostMapping("/auth/logout")
    public Msg logout(@RequestHeader(value = "Authorization", required = false) String h) { auth.logout(h); return new Msg("Logged out"); }
    @PostMapping("/auth/forgot")
    public Msg forgot(@Valid @RequestBody ForgotRequest r) { auth.forgotPassword(r.email()); return new Msg("If the email is registered, a reset code was sent."); }
    @PostMapping("/auth/reset")
    public Msg reset(@Valid @RequestBody ResetRequest r) { auth.resetPassword(r); return new Msg("Password updated"); }

    @GetMapping("/profile")
    public ProfileDto profile() { return auth.profile(Sec.me().id()); }
    @PutMapping("/profile")
    public ProfileDto update(@Valid @RequestBody ProfileUpdate r) { return auth.updateProfile(Sec.me().id(), r); }
    @PostMapping("/profile/password")
    public Msg password(@Valid @RequestBody ChangePasswordRequest r) { auth.changePassword(Sec.me().id(), r); return new Msg("Password changed"); }
}
