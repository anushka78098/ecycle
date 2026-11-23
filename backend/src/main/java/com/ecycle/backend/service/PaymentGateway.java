package com.ecycle.backend.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Provider abstraction. The backend always verifies payment itself; a client-reported "success" is never trusted. */
public interface PaymentGateway {
    String name();
    boolean sandbox();
    String keyId();
    /** Creates a provider-side order for the given amount (INR) and returns its id. */
    String createOrder(BigDecimal amount, String receipt);
    /** Verifies the provider signature over orderId|paymentId. */
    boolean verify(String providerOrderId, String providerPaymentId, String signature);
    /** Refunds (part of) a captured payment and returns the provider refund id. */
    String refund(String providerPaymentId, BigDecimal amount);

    static String hmacHex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
