package com.ecycle.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import com.ecycle.backend.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Claim checks only (no network): audience, issuer, verified e-mail and required fields. */
class GoogleTokenVerifierTest {
    private static final ObjectMapper M = new ObjectMapper();
    private static final String CID = "client-123.apps.googleusercontent.com";

    private static com.fasterxml.jackson.databind.JsonNode claims(String aud, String iss, String verified, String sub, String email) throws Exception {
        return M.readTree("{\"aud\":\"" + aud + "\",\"iss\":\"" + iss + "\",\"email_verified\":\"" + verified + "\",\"sub\":\"" + sub + "\",\"email\":\"" + email + "\",\"name\":\"Asha\"}");
    }

    @Test void acceptsAValidToken() throws Exception {
        var id = GoogleTokenVerifier.check(claims(CID, "https://accounts.google.com", "true", "1234", "Asha@Example.com"), CID);
        assertEquals("1234", id.sub()); assertEquals("asha@example.com", id.email());
    }
    @Test void rejectsAnotherAppsToken() { assertThrows(ApiException.class, () -> GoogleTokenVerifier.check(claims("other", "accounts.google.com", "true", "1", "a@b.c"), CID)); }
    @Test void rejectsUnexpectedIssuer() { assertThrows(ApiException.class, () -> GoogleTokenVerifier.check(claims(CID, "https://evil.example", "true", "1", "a@b.c"), CID)); }
    @Test void rejectsUnverifiedEmail() { assertThrows(ApiException.class, () -> GoogleTokenVerifier.check(claims(CID, "accounts.google.com", "false", "1", "a@b.c"), CID)); }
    @Test void rejectsMissingSubject() { assertThrows(ApiException.class, () -> GoogleTokenVerifier.check(claims(CID, "accounts.google.com", "true", "", "a@b.c"), CID)); }
}
