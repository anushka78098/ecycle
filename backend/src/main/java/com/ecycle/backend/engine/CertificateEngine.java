package com.ecycle.backend.engine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

/**
 * Content hashing and keyed signing for recycling / data-destruction certificates.
 *
 * contentHash = SHA-256(canonical payload); signature = HMAC-SHA256(secret, certificateNo | verificationCode | contentHash).
 * A certificate can therefore only be validated by a party that holds the server secret (the E-CYCLE verification endpoint); the PDF carries the
 * content hash and a QR code that points at that endpoint. Pure JDK.
 */
public final class CertificateEngine {
    private CertificateEngine() {}
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RND = new SecureRandom();

    /** Sorted key=value canonical form with length prefixes (immune to delimiter injection). */
    public static String canonical(Map<String, String> payload) {
        StringBuilder sb = new StringBuilder("ecycle-cert-v1|");
        for (var e : new TreeMap<>(payload).entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue();
            sb.append(e.getKey().length()).append(':').append(e.getKey()).append('=').append(v.length()).append(':').append(v).append(';');
        }
        return sb.toString();
    }
    public static String contentHash(Map<String, String> payload) { return HashChain.sha256Hex(canonical(payload)); }

    public static String sign(String secret, String certificateNo, String verificationCode, String contentHash) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((certificateNo + "|" + verificationCode + "|" + contentHash).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) { throw new IllegalStateException("HMAC unavailable", e); }
    }
    public static boolean verify(String secret, String certificateNo, String verificationCode, Map<String, String> payload, String storedHash, String storedSignature) {
        String h = contentHash(payload);
        if (!MessageDigest.isEqual(h.getBytes(StandardCharsets.UTF_8), String.valueOf(storedHash).getBytes(StandardCharsets.UTF_8))) return false;
        String sig = sign(secret, certificateNo, verificationCode, h);
        return MessageDigest.isEqual(sig.getBytes(StandardCharsets.UTF_8), String.valueOf(storedSignature).getBytes(StandardCharsets.UTF_8));
    }
    /** XXXX-XXXX-XXXX (about 60 bits), printed on the certificate and required by the public verification endpoint. */
    public static String newVerificationCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) { if (i > 0 && i % 4 == 0) sb.append('-'); sb.append(CODE_ALPHABET.charAt(RND.nextInt(CODE_ALPHABET.length()))); }
        return sb.toString();
    }
    public static String number(String prefix, LocalDate date, long seq) { return prefix + "-" + date.toString().replace("-", "") + "-" + String.format("%06d", seq); }
}
