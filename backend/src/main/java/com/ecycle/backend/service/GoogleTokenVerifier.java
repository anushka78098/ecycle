package com.ecycle.backend.service;

import com.ecycle.backend.config.AppProps;
import com.ecycle.backend.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Verifies a Google ID token by asking Google's tokeninfo endpoint (it checks the signature and expiry),
 * then checks audience, issuer and e-mail verification ourselves. Needs GOOGLE_CLIENT_ID.
 */
@Component @RequiredArgsConstructor
public class GoogleTokenVerifier {
    public record GoogleIdentity(String sub, String email, String name) {}
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private final AppProps props;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public boolean enabled() { return !props.getGoogleClientId().isBlank(); }

    public GoogleIdentity verify(String idToken) {
        if (!enabled()) throw ApiException.badRequest("Google sign-in is not configured on this server (set GOOGLE_CLIENT_ID).");
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + URLEncoder.encode(idToken, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw ApiException.unauthorized("Google rejected the sign-in token");
            return check(mapper.readTree(res.body()), props.getGoogleClientId());
        } catch (IOException e) {
            throw ApiException.badRequest("Could not reach Google to verify the sign-in: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.badRequest("Google sign-in was interrupted");
        }
    }

    /** Pure claim checks, separated so they can be unit-tested without network access. */
    static GoogleIdentity check(JsonNode c, String clientId) {
        if (!clientId.equals(c.path("aud").asText())) throw ApiException.unauthorized("This Google token was issued for a different application");
        if (!ISSUERS.contains(c.path("iss").asText())) throw ApiException.unauthorized("Unexpected token issuer");
        if (!"true".equalsIgnoreCase(c.path("email_verified").asText())) throw ApiException.unauthorized("Your Google e-mail address is not verified");
        String sub = c.path("sub").asText(""), email = c.path("email").asText("");
        if (sub.isBlank() || email.isBlank()) throw ApiException.unauthorized("The Google token has no account details");
        return new GoogleIdentity(sub, email.toLowerCase(), c.path("name").asText(""));
    }
}
