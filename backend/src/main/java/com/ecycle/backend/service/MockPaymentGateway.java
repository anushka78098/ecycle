package com.ecycle.backend.service;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** SANDBOX adapter used when no Razorpay credentials exist. It signs with a fixed development secret; it is not a real payment and must not be used in production. */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {
    private static final String SECRET = "ecycle-mock-sandbox-secret";
    public String name() { return "MOCK_SANDBOX"; }
    public boolean sandbox() { return true; }
    public String keyId() { return "mock_key"; }
    public String createOrder(BigDecimal amount, String receipt) { return "mock_order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16); }
    public boolean verify(String orderId, String paymentId, String signature) {
        return signature != null && PaymentGateway.constantTimeEquals(sign(orderId, paymentId), signature);
    }
    public String refund(String paymentId, BigDecimal amount) { return "mock_rfnd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    /** Simulates the provider's checkout callback; used only by the sandbox confirm endpoint. */
    public String sign(String orderId, String paymentId) { return PaymentGateway.hmacHex(SECRET, orderId + "|" + paymentId); }
}
