package com.ecycle.frontend.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Blocking HTTP client for the Spring Boot REST API. Every call here must run off the JavaFX thread; use Async.run(...).
 * The frontend never touches the database: this class is its only data source.
 */
public final class ApiClient {
    private ApiClient() {}
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    public static final String BASE = System.getenv().getOrDefault("ECYCLE_API", "http://localhost:8080");

    public static ObjectMapper mapper() { return MAPPER; }

    private static HttpRequest.Builder req(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(45));
        if (Session.token != null) b.header("Authorization", "Bearer " + Session.token);
        return b;
    }
    private static HttpRequest.BodyPublisher json(Object body) {
        try { return HttpRequest.BodyPublishers.ofString(body == null ? "{}" : MAPPER.writeValueAsString(body)); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }

    public static JsonNode get(String path) { return send(req(path).GET().build()); }
    public static JsonNode post(String path, Object body) { return send(req(path).header("Content-Type", "application/json").POST(json(body)).build()); }
    public static JsonNode put(String path, Object body) { return send(req(path).header("Content-Type", "application/json").PUT(json(body)).build()); }
    public static JsonNode delete(String path) { return send(req(path).DELETE().build()); }

    public static byte[] bytes(String path) {
        try {
            HttpResponse<byte[]> res = HTTP.send(req(path).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() >= 400) throw toError(res.statusCode(), new String(res.body(), StandardCharsets.UTF_8));
            return res.body();
        } catch (IOException e) { throw unreachable(e); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ApiError(0, "Request interrupted", null); }
    }

    /** Multipart upload: simple text fields plus one or more files under the same part name. */
    public static JsonNode upload(String path, Map<String, String> fields, String fileField, List<File> files) {
        String boundary = "----ecycle" + UUID.randomUUID();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (var e : fields.entrySet()) {
                if (e.getValue() == null) continue;
                out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + e.getKey() + "\"\r\n\r\n" + e.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
            for (File f : files) {
                out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + fileField + "\"; filename=\"" + f.getName().replace("\"", "") + "\"\r\nContent-Type: " + mime(f) + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(Files.readAllBytes(f.toPath())); out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return send(req(path).header("Content-Type", "multipart/form-data; boundary=" + boundary).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build());
        } catch (IOException e) { throw new ApiError(0, "Could not read the selected file: " + e.getMessage(), null); }
    }
    private static String mime(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) return "image/png"; if (n.endsWith(".webp")) return "image/webp"; if (n.endsWith(".pdf")) return "application/pdf";
        return "image/jpeg";
    }

    private static JsonNode send(HttpRequest r) {
        try {
            HttpResponse<String> res = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) throw toError(res.statusCode(), res.body());
            return res.body() == null || res.body().isBlank() ? MissingNode.getInstance() : MAPPER.readTree(res.body());
        } catch (IOException e) { throw unreachable(e); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ApiError(0, "Request interrupted", null); }
    }
    private static ApiError unreachable(IOException e) { return new ApiError(0, "Cannot reach the E-CYCLE server at " + BASE + ". Is the backend running? (" + e.getMessage() + ")", null); }
    private static ApiError toError(int status, String body) {
        try {
            JsonNode n = MAPPER.readTree(body);
            Map<String, String> f = new java.util.LinkedHashMap<>();
            n.path("fieldErrors").fields().forEachRemaining(e -> f.put(e.getKey(), e.getValue().asText()));
            return new ApiError(status, n.path("message").asText("Request failed (HTTP " + status + ")"), f);
        } catch (IOException e) { return new ApiError(status, "Request failed (HTTP " + status + ")", null); }
    }
}
