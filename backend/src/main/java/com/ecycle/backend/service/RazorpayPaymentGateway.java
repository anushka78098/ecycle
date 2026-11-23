package com.ecycle.backend.service;

import com.ecycle.backend.config.AppProps;
import com.ecycle.backend.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Razorpay integration (orders, signature verification, refunds). Activate with PAYMENT_PROVIDER=razorpay plus RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET (test-mode keys for sandbox). */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "razorpay")
public class RazorpayPaymentGateway implements PaymentGateway {
    private static final String BASE = "https://api.razorpay.com/v1";
    private final String keyId, keySecret; private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public RazorpayPaymentGateway(AppProps p, ObjectMapper json) {
        if (p.getRazorpayKeyId().isBlank() || p.getRazorpayKeySecret().isBlank()) throw new IllegalStateException("RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET are required when PAYMENT_PROVIDER=razorpay");
        this.keyId = p.getRazorpayKeyId(); this.keySecret = p.getRazorpayKeySecret(); this.json = json;
    }
    public String name() { return "RAZORPAY"; }
    public boolean sandbox() { return keyId.startsWith("rzp_test_"); }
    public String keyId() { return keyId; }

    private static long paise(BigDecimal inr) { return inr.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact(); }

    private JsonNode post(String path, Map<String, Object> body) {
        try {
            String auth = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(20)).header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) throw new ApiException(HttpStatus.BAD_GATEWAY, "Payment provider rejected the request (HTTP " + res.statusCode() + ")");
            return json.readTree(res.body());
        } catch (IOException e) { throw new ApiException(HttpStatus.BAD_GATEWAY, "Payment provider unreachable: " + e.getMessage());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ApiException(HttpStatus.BAD_GATEWAY, "Payment request interrupted"); }
    }
    public String createOrder(BigDecimal amount, String receipt) {
        return post("/orders", Map.of("amount", paise(amount), "currency", "INR", "receipt", receipt)).path("id").asText();
    }
    public boolean verify(String orderId, String paymentId, String signature) {
        return signature != null && PaymentGateway.constantTimeEquals(PaymentGateway.hmacHex(keySecret, orderId + "|" + paymentId), signature);
    }
    public String refund(String paymentId, BigDecimal amount) {
        return post("/payments/" + paymentId + "/refund", Map.of("amount", paise(amount))).path("id").asText();
    }
}
