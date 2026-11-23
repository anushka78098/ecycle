package com.ecycle.backend.engine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Tamper-evident SHA-256 hash chain used for the QR chain of custody.
 * Every entry commits to its own fields AND to the hash of the previous entry of the same asset, so changing, deleting,
 * inserting or re-ordering any historic entry breaks every later hash. Pure Java, no framework dependency.
 *
 * Canonical form: "ecycle-custody-v1" followed by each field as {@code <length>:<value>} (null is {@code n:}), so field boundaries are
 * unambiguous and no delimiter can be smuggled inside a value.
 */
public final class HashChain {
    private HashChain() {}
    public static final String GENESIS = "0".repeat(64);
    public static final String VERSION = "ecycle-custody-v1";

    public record Entry(String assetCode, int seq, String eventType, Long actorId, String actorRole, Long custodianId,
                        String location, String detail, long occurredAtMillis, String prevHash, String hash) {}
    public record Verification(boolean valid, int checked, Integer brokenAtSeq, String reason, String headHash) {}

    private static void field(StringBuilder sb, String v) { if (v == null) sb.append("n:"); else sb.append(v.length()).append(':').append(v); sb.append(';'); }

    public static String canonical(String assetCode, int seq, String eventType, Long actorId, String actorRole, Long custodianId,
                                   String location, String detail, long occurredAtMillis, String prevHash) {
        StringBuilder sb = new StringBuilder(VERSION).append('|');
        field(sb, assetCode); field(sb, String.valueOf(seq)); field(sb, eventType); field(sb, actorId == null ? null : actorId.toString());
        field(sb, actorRole); field(sb, custodianId == null ? null : custodianId.toString()); field(sb, location); field(sb, detail);
        field(sb, String.valueOf(occurredAtMillis)); field(sb, prevHash);
        return sb.toString();
    }

    public static String sha256Hex(String s) { return sha256Hex(s.getBytes(StandardCharsets.UTF_8)); }
    public static String sha256Hex(byte[] b) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    public static String hashOf(String assetCode, int seq, String eventType, Long actorId, String actorRole, Long custodianId,
                                String location, String detail, long occurredAtMillis, String prevHash) {
        return sha256Hex(canonical(assetCode, seq, eventType, actorId, actorRole, custodianId, location, detail, occurredAtMillis, prevHash));
    }

    public static String hashOf(Entry e) {
        return hashOf(e.assetCode(), e.seq(), e.eventType(), e.actorId(), e.actorRole(), e.custodianId(), e.location(), e.detail(), e.occurredAtMillis(), e.prevHash());
    }

    /** Re-computes every hash and link. Entries must be supplied in ascending seq order for ONE asset. */
    public static Verification verify(List<Entry> entries) {
        String prev = GENESIS; int n = 0;
        for (Entry e : entries) {
            n++;
            if (e.seq() != n) return new Verification(false, n - 1, e.seq(), "Sequence gap or reordering: expected #" + n + " but found #" + e.seq(), prev);
            if (!prev.equals(e.prevHash())) return new Verification(false, n - 1, e.seq(), "Entry #" + e.seq() + " does not link to the previous entry (an earlier entry was altered, removed or inserted)", prev);
            String h = hashOf(e);
            if (!h.equals(e.hash())) return new Verification(false, n - 1, e.seq(), "Entry #" + e.seq() + " was modified after it was recorded (hash mismatch)", prev);
            prev = e.hash();
        }
        return new Verification(true, n, null, n == 0 ? "Chain is empty" : "All " + n + " entries verified", prev);
    }
}
