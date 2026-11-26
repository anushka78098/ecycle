package com.ecycle.backend.service;

import com.ecycle.backend.config.AppProps;
import com.ecycle.backend.config.JwtService;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    static final int MAX_FAILED = 5;
    private final UserRepository users; private final AppRoleRepository roles; private final SellerProfileRepository sellers;
    private final BuyerProfileRepository buyers; private final PasswordResetTokenRepository resets; private final TokenRevocationRepository revoked;
    private final PasswordEncoder encoder; private final JwtService jwt; private final AuditService audit; private final EmailService email; private final AppProps props;

    @Transactional
    public AuthResponse register(RegisterRequest r) {
        if (r.role() != Role.SELLER && r.role() != Role.BUYER) throw ApiException.badRequest("Only SELLER and BUYER accounts can self-register");
        if (users.existsByEmailIgnoreCase(r.email())) throw ApiException.conflict("An account with this email already exists");
        AppUser u = createUser(r.email(), r.password(), r.fullName(), r.phone(), r.role());
        if (r.role() == Role.SELLER) {
            SellerProfile p = new SellerProfile(); p.setUserId(u.getId()); p.setBusinessName(r.businessName()); p.setCity(r.city()); sellers.save(p);
        } else {
            BuyerProfile p = new BuyerProfile(); p.setUserId(u.getId()); p.setOrganization(r.organization()); p.setCity(r.city());
            p.setBuyerType(r.buyerType() == null ? BuyerType.INDIVIDUAL : r.buyerType()); buyers.save(p);
        }
        audit.log(u.getId(), "USER_REGISTERED", "users", u.getId(), "role=" + r.role());
        return issue(u);
    }

    /** Creates an account with the given role. Used by registration, admin staff creation and bootstrap seeding. */
    @Transactional
    public AppUser createUser(String email, String rawPassword, String fullName, String phone, Role role) {
        AppUser u = new AppUser();
        u.setEmail(email.trim().toLowerCase()); u.setPasswordHash(encoder.encode(rawPassword)); u.setFullName(fullName.trim()); u.setPhone(phone);
        u.getRoles().add(roles.findByName(role.name()).orElseThrow(() -> new IllegalStateException("Role missing: " + role)));
        return users.save(u);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest r) {
        AppUser u = users.findByEmailIgnoreCase(r.email().trim()).orElse(null);
        if (u == null) { encoder.matches(r.password(), "$2a$12$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvalidinva"); throw ApiException.unauthorized("Invalid email or password"); }
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(Instant.now())) throw ApiException.forbidden("Account temporarily locked after repeated failures. Try again later.");
        if (!encoder.matches(r.password(), u.getPasswordHash())) {
            u.setFailedLogins(u.getFailedLogins() + 1);
            if (u.getFailedLogins() >= MAX_FAILED) { u.setLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES)); u.setFailedLogins(0); audit.log(u.getId(), "ACCOUNT_LOCKED", "users", u.getId(), "too many failed logins"); }
            throw ApiException.unauthorized("Invalid email or password");
        }
        if (u.getStatus() != AccountStatus.ACTIVE) throw ApiException.forbidden("This account is suspended");
        u.setFailedLogins(0); u.setLockedUntil(null);
        audit.log(u.getId(), "LOGIN", "users", u.getId(), null);
        return issue(u);
    }

    /** Sign in (or first-time sign up) with a Google identity already verified by GoogleTokenVerifier. Resolvers/staff can sign in but never self-create here. */
    @Transactional
    public AuthResponse googleLogin(GoogleTokenVerifier.GoogleIdentity g, Role roleIfNew) {
        AppUser u = users.findByGoogleSub(g.sub()).orElseGet(() -> users.findByEmailIgnoreCase(g.email()).orElse(null));
        if (u == null) {
            if (roleIfNew != Role.SELLER && roleIfNew != Role.BUYER) throw ApiException.badRequest("New to E-CYCLE? Choose Seller or Buyer to create your account with Google.");
            byte[] raw = new byte[24]; new SecureRandom().nextBytes(raw);
            String name = g.name() == null || g.name().isBlank() ? g.email() : g.name();
            u = createUser(g.email(), HexFormat.of().formatHex(raw), name.length() > 120 ? name.substring(0, 120) : name, null, roleIfNew);
            if (roleIfNew == Role.SELLER) { SellerProfile p = new SellerProfile(); p.setUserId(u.getId()); sellers.save(p); }
            else { BuyerProfile p = new BuyerProfile(); p.setUserId(u.getId()); p.setBuyerType(BuyerType.INDIVIDUAL); buyers.save(p); }
            audit.log(u.getId(), "USER_REGISTERED", "users", u.getId(), "google role=" + roleIfNew);
        }
        if (u.getStatus() != AccountStatus.ACTIVE) throw ApiException.forbidden("This account is suspended");
        if (u.getGoogleSub() == null) u.setGoogleSub(g.sub());
        else if (!u.getGoogleSub().equals(g.sub())) throw ApiException.forbidden("This email is linked to a different Google account");
        audit.log(u.getId(), "LOGIN_GOOGLE", "users", u.getId(), null);
        return issue(u);
    }

    private AuthResponse issue(AppUser u) {
        var t = jwt.issue(u);
        return new AuthResponse(t.token(), jwt.expirySeconds(), u.getId(), u.getFullName(), u.getEmail(), u.roleSet());
    }

    @Transactional
    public void logout(String bearer) {
        if (bearer == null || !bearer.startsWith("Bearer ")) return;
        try {
            Claims c = jwt.parse(bearer.substring(7));
            if (!revoked.existsByJti(c.getId())) {
                TokenRevocation t = new TokenRevocation(); t.setJti(c.getId()); t.setExpiresAt(c.getExpiration().toInstant()); revoked.save(t);
            }
            revoked.purge(Instant.now());
        } catch (JwtException | IllegalArgumentException e) { log.debug("Logout with invalid token ignored: {}", e.getMessage()); }
    }

    @Transactional
    public void forgotPassword(String emailAddr) {
        users.findByEmailIgnoreCase(emailAddr.trim()).ifPresent(u -> {
            byte[] raw = new byte[24]; new SecureRandom().nextBytes(raw);
            String token = HexFormat.of().formatHex(raw);
            PasswordResetToken t = new PasswordResetToken(); t.setUserId(u.getId()); t.setTokenHash(sha(token)); t.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES)); resets.save(t);
            email.send(u.getEmail(), "Reset your E-CYCLE password", "Use this reset code within 30 minutes: " + token);
            if (props.isDevMode() && !props.isMailEnabled()) log.warn("DEV MODE reset code for {}: {}", u.getEmail(), token);
            audit.log(u.getId(), "PASSWORD_RESET_REQUESTED", "users", u.getId(), null);
        });
        // Always succeed silently so the endpoint cannot be used to discover which emails are registered.
    }

    @Transactional
    public void resetPassword(ResetRequest r) {
        PasswordResetToken t = resets.findByTokenHash(sha(r.token())).orElseThrow(() -> ApiException.badRequest("Invalid or expired reset code"));
        if (t.isUsed() || t.getExpiresAt().isBefore(Instant.now())) throw ApiException.badRequest("Invalid or expired reset code");
        AppUser u = users.findById(t.getUserId()).orElseThrow(() -> ApiException.badRequest("Invalid or expired reset code"));
        u.setPasswordHash(encoder.encode(r.newPassword())); u.setFailedLogins(0); u.setLockedUntil(null); t.setUsed(true);
        audit.log(u.getId(), "PASSWORD_RESET", "users", u.getId(), null);
    }

    @Transactional
    public void changePassword(Long uid, ChangePasswordRequest r) {
        AppUser u = users.findById(uid).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!encoder.matches(r.currentPassword(), u.getPasswordHash())) throw ApiException.badRequest("Current password is incorrect");
        u.setPasswordHash(encoder.encode(r.newPassword()));
        audit.log(uid, "PASSWORD_CHANGED", "users", uid, null);
    }

    @Transactional(readOnly = true)
    public ProfileDto profile(Long uid) {
        AppUser u = users.findById(uid).orElseThrow(() -> ApiException.notFound("User not found"));
        var sp = sellers.findByUserId(uid).orElse(null); var bp = buyers.findByUserId(uid).orElse(null);
        String city = sp != null ? sp.getCity() : bp != null ? bp.getCity() : null;
        String org = sp != null ? sp.getBusinessName() : bp != null ? bp.getOrganization() : null;
        VerificationStatus vs = sp != null ? sp.getVerificationStatus() : bp != null ? (bp.isVerified() ? VerificationStatus.VERIFIED : VerificationStatus.PENDING) : null;
        return new ProfileDto(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.roleSet(), u.getStatus(), city, org, vs,
                bp == null ? null : bp.getBuyerType(), bp == null ? 0 : bp.getRecoveryCapability(), bp == null ? 0 : bp.getEnvironmentalRating(),
                sp == null ? 0 : sp.getTrustScore().doubleValue());
    }

    @Transactional
    public ProfileDto updateProfile(Long uid, ProfileUpdate r) {
        AppUser u = users.findById(uid).orElseThrow(() -> ApiException.notFound("User not found"));
        u.setFullName(r.fullName().trim()); u.setPhone(r.phone());
        sellers.findByUserId(uid).ifPresent(p -> { p.setCity(r.city()); p.setBusinessName(r.organization()); });
        buyers.findByUserId(uid).ifPresent(p -> { p.setCity(r.city()); p.setOrganization(r.organization()); });
        audit.log(uid, "PROFILE_UPDATED", "users", uid, null);
        return profile(uid);
    }

    private static String sha(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
