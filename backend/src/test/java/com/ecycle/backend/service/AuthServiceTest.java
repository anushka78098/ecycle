package com.ecycle.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ecycle.backend.config.AppProps;
import com.ecycle.backend.config.JwtService;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class) @MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {
    @Mock UserRepository users; @Mock AppRoleRepository roles; @Mock SellerProfileRepository sellers; @Mock BuyerProfileRepository buyers; @Mock PasswordResetTokenRepository resets;
    @Mock TokenRevocationRepository revoked; @Mock PasswordEncoder encoder; @Mock JwtService jwt; @Mock AuditService audit; @Mock EmailService email; @Mock AppProps props;
    @InjectMocks AuthService svc;

    @BeforeEach void setUp() {
        when(users.save(any())).thenAnswer(i -> { AppUser u = i.getArgument(0); u.setId(7L); return u; });
        when(encoder.encode(any())).thenReturn("$2a$hash");
        when(jwt.issue(any())).thenReturn(new JwtService.Issued("tok", "jti", Instant.now().plusSeconds(60))); when(jwt.expirySeconds()).thenReturn(3600L);
        for (Role r : Role.values()) { AppRole ar = new AppRole(); ar.setName(r.name()); when(roles.findByName(r.name())).thenReturn(Optional.of(ar)); }
    }
    private static RegisterRequest reg(Role r, String email) { return new RegisterRequest(email, "Password1", "Ann Lee", null, r, "Biz", "Pune", null, null); }
    private AppUser user(int failed) {
        AppUser u = new AppUser(); u.setId(7L); u.setEmail("a@b.c"); u.setPasswordHash("$2a$hash"); u.setFullName("A"); u.setFailedLogins(failed); return u;
    }

    @Test void registrationCreatesSellerWithHashedPasswordAndProfile() {
        AuthResponse r = svc.register(reg(Role.SELLER, "Seller@Example.com"));
        assertTrue(r.roles().contains(Role.SELLER)); assertEquals("tok", r.token());
        ArgumentCaptor<AppUser> c = ArgumentCaptor.forClass(AppUser.class); verify(users).save(c.capture());
        assertEquals("seller@example.com", c.getValue().getEmail()); assertEquals("$2a$hash", c.getValue().getPasswordHash()); assertNotEquals("Password1", c.getValue().getPasswordHash());
        verify(sellers).save(any(SellerProfile.class)); verify(buyers, never()).save(any());
    }
    @Test void buyerRegistrationCreatesBuyerProfile() { svc.register(reg(Role.BUYER, "b@x.com")); verify(buyers).save(any(BuyerProfile.class)); }
    @Test void privilegedRolesCannotSelfRegister() {
        ApiException e = assertThrows(ApiException.class, () -> svc.register(reg(Role.RESOLVER, "x@y.com"))); assertEquals(HttpStatus.BAD_REQUEST, e.status());
        assertThrows(ApiException.class, () -> svc.register(reg(Role.RESOLVER, "x@y.com"))); verify(users, never()).save(any());
    }
    @Test void duplicateEmailIsRejected() {
        when(users.existsByEmailIgnoreCase("dup@x.com")).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> svc.register(reg(Role.BUYER, "dup@x.com"))).status());
    }
    @Test void loginSucceedsWithCorrectPassword() {
        AppUser u = user(2); when(users.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(u)); when(encoder.matches("Password1", "$2a$hash")).thenReturn(true);
        assertEquals("tok", svc.login(new LoginRequest("a@b.c", "Password1")).token()); assertEquals(0, u.getFailedLogins());
    }
    @Test void wrongPasswordIsUnauthorizedAndCounted() {
        AppUser u = user(0); when(users.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(u)); when(encoder.matches(any(), any())).thenReturn(false);
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ApiException.class, () -> svc.login(new LoginRequest("a@b.c", "bad"))).status()); assertEquals(1, u.getFailedLogins());
    }
    @Test void fifthFailureLocksTheAccount() {
        AppUser u = user(4); when(users.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(u)); when(encoder.matches(any(), any())).thenReturn(false);
        assertThrows(ApiException.class, () -> svc.login(new LoginRequest("a@b.c", "bad"))); assertNotNull(u.getLockedUntil()); assertTrue(u.getLockedUntil().isAfter(Instant.now()));
    }
    @Test void lockedAccountCannotLoginEvenWithCorrectPassword() {
        AppUser u = user(0); u.setLockedUntil(Instant.now().plusSeconds(300)); when(users.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(u)); when(encoder.matches(any(), any())).thenReturn(true);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> svc.login(new LoginRequest("a@b.c", "Password1"))).status());
    }
    @Test void suspendedAccountCannotLogin() {
        AppUser u = user(0); u.setStatus(AccountStatus.SUSPENDED); when(users.findByEmailIgnoreCase("a@b.c")).thenReturn(Optional.of(u)); when(encoder.matches(any(), any())).thenReturn(true);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> svc.login(new LoginRequest("a@b.c", "Password1"))).status());
    }
    @Test void unknownEmailGivesTheSameErrorAsWrongPassword() {
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        ApiException e = assertThrows(ApiException.class, () -> svc.login(new LoginRequest("no@x.com", "x"))); assertEquals("Invalid email or password", e.getMessage());
    }
    @Test void forgotPasswordForUnknownEmailDoesNothingVisible() {
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.empty()); assertDoesNotThrow(() -> svc.forgotPassword("no@x.com")); verify(resets, never()).save(any());
    }
    @Test void resetRejectsUsedOrExpiredCodes() {
        PasswordResetToken t = new PasswordResetToken(); t.setUserId(7L); t.setUsed(true); t.setExpiresAt(Instant.now().plusSeconds(60));
        when(resets.findByTokenHash(any())).thenReturn(Optional.of(t)); assertThrows(ApiException.class, () -> svc.resetPassword(new ResetRequest("code", "Newpass123")));
        t.setUsed(false); t.setExpiresAt(Instant.now().minusSeconds(1)); assertThrows(ApiException.class, () -> svc.resetPassword(new ResetRequest("code", "Newpass123")));
    }
}
