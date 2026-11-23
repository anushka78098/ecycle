package com.ecycle.frontend.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * "Sign in with Google" for a desktop app: OAuth 2.0 authorization-code flow with PKCE and a loopback redirect
 * (the method Google recommends for installed apps). The system browser handles the Google login, so the password
 * never touches this app. The resulting ID token is sent to the backend, which verifies it.
 * Needs a Google Cloud OAuth client of type "Desktop app": ECYCLE_GOOGLE_CLIENT_ID and ECYCLE_GOOGLE_CLIENT_SECRET.
 */
public final class GoogleSignIn {
    private GoogleSignIn() {}
    private static final String AUTH = "https://accounts.google.com/o/oauth2/v2/auth", TOKEN = "https://oauth2.googleapis.com/token";
    private static final String CLIENT_ID = System.getenv().getOrDefault("ECYCLE_GOOGLE_CLIENT_ID", "");
    private static final String CLIENT_SECRET = System.getenv().getOrDefault("ECYCLE_GOOGLE_CLIENT_SECRET", "");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public static boolean configured() { return !CLIENT_ID.isBlank(); }

    /** Blocking: opens the browser and waits for the user. Call it from a background thread (Async.run). */
    public static String obtainIdToken() {
        if (!configured()) throw new ApiError(0, "Google sign-in is not set up on this computer. Set ECYCLE_GOOGLE_CLIENT_ID and ECYCLE_GOOGLE_CLIENT_SECRET (a Google Cloud \"Desktop app\" OAuth client), then restart.", null);
        HttpServer server = null;
        try {
            SecureRandom rnd = new SecureRandom();
            String verifier = random(rnd, 32), state = random(rnd, 16);
            String challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            CompletableFuture<String> code = new CompletableFuture<>();
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/callback", ex -> {
                Map<String, String> q = parse(ex.getRequestURI().getRawQuery());
                String msg;
                if (q.containsKey("error")) { code.completeExceptionally(new ApiError(0, "Google sign-in was cancelled (" + q.get("error") + ").", null)); msg = "Sign-in cancelled. You can close this tab."; }
                else if (!state.equals(q.get("state")) || q.get("code") == null) { code.completeExceptionally(new ApiError(0, "Google returned an unexpected response. Please try again.", null)); msg = "Unexpected response. You can close this tab."; }
                else { code.complete(q.get("code")); msg = "Signed in. You can close this tab and return to E-CYCLE."; }
                byte[] body = ("<html><body style=\"font-family:sans-serif;text-align:center;margin-top:20vh\"><h2>" + msg + "</h2></body></html>").getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                ex.sendResponseHeaders(200, body.length);
                try (OutputStream o = ex.getResponseBody()) { o.write(body); }
            });
            server.start();
            String redirect = "http://127.0.0.1:" + server.getAddress().getPort() + "/callback";
            Map<String, String> p = new LinkedHashMap<>();
            p.put("client_id", CLIENT_ID); p.put("redirect_uri", redirect); p.put("response_type", "code"); p.put("scope", "openid email profile");
            p.put("code_challenge", challenge); p.put("code_challenge_method", "S256"); p.put("state", state); p.put("prompt", "select_account");
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                throw new ApiError(0, "This computer cannot open a web browser from the app. Use e-mail and password instead.", null);
            Desktop.getDesktop().browse(URI.create(AUTH + "?" + form(p)));
            String authCode = code.get(3, TimeUnit.MINUTES);

            Map<String, String> t = new LinkedHashMap<>();
            t.put("code", authCode); t.put("client_id", CLIENT_ID); t.put("client_secret", CLIENT_SECRET); t.put("redirect_uri", redirect);
            t.put("grant_type", "authorization_code"); t.put("code_verifier", verifier);
            HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(TOKEN)).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(t))).build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = ApiClient.mapper().readTree(res.body());
            if (res.statusCode() != 200 || !json.hasNonNull("id_token"))
                throw new ApiError(0, "Google did not return a sign-in token: " + json.path("error_description").asText(json.path("error").asText("unknown error")), null);
            return json.get("id_token").asText();
        } catch (ExecutionException e) {
            throw e.getCause() instanceof ApiError a ? a : new ApiError(0, "Google sign-in failed: " + e.getCause().getMessage(), null);
        } catch (TimeoutException e) {
            throw new ApiError(0, "Google sign-in timed out. Please try again.", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new ApiError(0, "Google sign-in was interrupted.", null);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new ApiError(0, "Google sign-in failed: " + e.getMessage(), null);
        } finally {
            if (server != null) server.stop(0);
        }
    }

    private static String random(SecureRandom r, int n) { byte[] b = new byte[n]; r.nextBytes(b); return b64(b); }
    private static String b64(byte[] b) { return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static String form(Map<String, String> m) {
        StringBuilder sb = new StringBuilder();
        m.forEach((k, v) -> { if (sb.length() > 0) sb.append('&'); sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8)); });
        return sb.toString();
    }
    private static Map<String, String> parse(String raw) {
        Map<String, String> m = new LinkedHashMap<>();
        if (raw == null) return m;
        for (String pair : raw.split("&")) { int i = pair.indexOf('='); if (i > 0) m.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8)); }
        return m;
    }
}
