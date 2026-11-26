package com.ecycle.backend.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.ecycle.shared.Enums.PickupStatus;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Engines behind chain of custody, certificates, recycler search, pickups, eco-points and wipe checklists. */
class FeatureEnginesTest {
    private static HashChain.Entry entry(int seq, String detail, String prev) {
        HashChain.Entry d = new HashChain.Entry("EW-1", seq, seq == 1 ? "CREATED" : "HANDOVER", 7L, "SELLER", 7L, "Ludhiana", detail, 1_700_000_000_000L + seq * 1000L, prev, "");
        return new HashChain.Entry(d.assetCode(), seq, d.eventType(), d.actorId(), d.actorRole(), d.custodianId(), d.location(), detail, d.occurredAtMillis(), prev, HashChain.hashOf(d));
    }
    private static List<HashChain.Entry> chain(int n) { List<HashChain.Entry> l = new ArrayList<>(); String prev = HashChain.GENESIS; for (int i = 1; i <= n; i++) { var e = entry(i, "step " + i, prev); l.add(e); prev = e.hash(); } return l; }

    @Test void chainVerifiesAndDetectsEditDeletionAndReordering() {
        var c = chain(5); assertTrue(HashChain.verify(c).valid());
        var edited = new ArrayList<>(c); var o = c.get(2);
        edited.set(2, new HashChain.Entry(o.assetCode(), o.seq(), o.eventType(), o.actorId(), o.actorRole(), o.custodianId(), o.location(), "tampered", o.occurredAtMillis(), o.prevHash(), o.hash()));
        var v = HashChain.verify(edited); assertFalse(v.valid()); assertEquals(3, v.brokenAtSeq());
        var removed = new ArrayList<>(c); removed.remove(1); assertFalse(HashChain.verify(removed).valid());
        var swapped = new ArrayList<>(c); Collections.swap(swapped, 1, 2); assertFalse(HashChain.verify(swapped).valid());
    }
    @Test void tailTruncationNeedsTheCertificateAnchor() { var c = chain(5); assertTrue(HashChain.verify(c.subList(0, 3)).valid()); assertNotEquals(c.get(4).hash(), HashChain.verify(c.subList(0, 3)).headHash()); }
    @Test void sha256KnownAnswer() { assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", HashChain.sha256Hex("abc")); }

    @Test void certificateSignatureBindsNumberCodePayloadAndKey() {
        Map<String, String> p = new TreeMap<>(Map.of("items", "Laptop", "weightKg", "1.6"));
        String h = CertificateEngine.contentHash(p), code = CertificateEngine.newVerificationCode(), sig = CertificateEngine.sign("k1", "REC-1", code, h);
        assertTrue(CertificateEngine.verify("k1", "REC-1", code, p, h, sig));
        Map<String, String> q = new TreeMap<>(p); q.put("weightKg", "16");
        assertFalse(CertificateEngine.verify("k1", "REC-1", code, q, h, sig)); assertFalse(CertificateEngine.verify("k2", "REC-1", code, p, h, sig));
        assertFalse(CertificateEngine.verify("k1", "REC-2", code, p, h, sig)); assertFalse(CertificateEngine.verify("k1", "REC-1", "AAAA-AAAA-AAAA", p, h, sig));
        assertTrue(code.matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}"));
    }

    @Test void geoDistanceAndBoundingBox() {
        assertEquals(1153, GeoMath.haversineKm(28.6139, 77.2090, 19.0760, 72.8777), 15);
        double[] b = GeoMath.boundingBox(30.9, 75.85, 50); assertTrue(b[0] < 30.9 && b[1] > 30.9 && b[2] < 75.85 && b[3] > 75.85);
        assertTrue(GeoMath.validLatLon(0.0, 0.0)); assertFalse(GeoMath.validLatLon(91.0, 0.0)); assertFalse(GeoMath.validLatLon(null, 1.0));
    }

    @Test void pickupStateMachine() {
        var R = PickupFlow.Actor.RECYCLER; var Q = PickupFlow.Actor.REQUESTER;
        assertTrue(PickupFlow.canTransition(PickupStatus.REQUESTED, PickupStatus.ACCEPTED, R)); assertFalse(PickupFlow.canTransition(PickupStatus.REQUESTED, PickupStatus.ACCEPTED, Q));
        assertTrue(PickupFlow.canTransition(PickupStatus.REQUESTED, PickupStatus.CANCELLED, Q)); assertFalse(PickupFlow.canTransition(PickupStatus.PICKED_UP, PickupStatus.CANCELLED, Q));
        assertFalse(PickupFlow.canTransition(PickupStatus.REQUESTED, PickupStatus.PICKED_UP, R));
        for (PickupStatus s : List.of(PickupStatus.COMPLETED, PickupStatus.REJECTED, PickupStatus.CANCELLED)) for (var a : PickupFlow.Actor.values()) assertTrue(PickupFlow.allowedNext(s, a).isEmpty());
    }

    @Test void ecoMath() {
        assertEquals(1, EcoEngine.level(0)); assertEquals(2, EcoEngine.level(100)); assertEquals(3, EcoEngine.level(400));
        assertEquals(15, EcoEngine.pointsForWeight(6, 2.5)); assertEquals(1, EcoEngine.pointsForWeight(6, 0.01)); assertEquals(0, EcoEngine.pointsForWeight(6, -1));
        var e = EcoEngine.equivalents(42); assertEquals(2.0, e.treeYears(), 1e-9); assertEquals(168, e.carKm(), 1e-9);
        var r = EcoEngine.rank(List.of(300L, 300L, 200L, 100L)); assertEquals(List.of(1, 1, 3, 4), r.stream().map(EcoEngine.Ranked::rank).toList());
        assertEquals("Asha K.", EcoEngine.publicName("Asha Kumar")); assertEquals("Eco member", EcoEngine.publicName(" "));
        assertEquals(0.5, EcoEngine.challengeFraction(5, 10)); assertEquals(1, EcoEngine.challengeFraction(15, 10));
    }

    @Test void wipeChecklistValidation() {
        List<String> all = WipeChecklists.forCategory("Laptops").stream().map(WipeChecklists.Item::code).toList();
        assertTrue(WipeChecklists.missingMandatory("Laptops", all).isEmpty()); assertFalse(WipeChecklists.missingMandatory("Laptops", List.of()).isEmpty());
        assertEquals(List.of("NOPE"), WipeChecklists.unknownCodes("Laptops", List.of("NOPE")));
        assertTrue(WipeChecklists.knownMethod("NIST 800-88 Purge")); assertFalse(WipeChecklists.knownMethod("rm -rf"));
        assertFalse(WipeChecklists.forCategory(null).isEmpty());
    }

    @Test void certificatePdfIsWellFormed() {
        boolean[][] qr = new boolean[21][21]; for (int i = 0; i < 21; i++) qr[i][i] = true;
        byte[] pdf = CertificateRenderer.render(new CertificateRenderer.Input("Certificate of Recycling", "REC-1", "ABCD-2345-WXYZ", "2026-11-18 10:00 UTC", List.<String[]>of(new String[]{"Equipment", "Laptop (a) \\ b"}), "Statement", "hash", null, "http://x/y", qr, "Issued by: T"), Instant.parse("2026-11-18T10:00:00Z"));
        String t = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1); assertTrue(t.startsWith("%PDF-1.4")); assertTrue(t.trim().endsWith("%%EOF")); assertTrue(t.contains("\\(a\\)"));
    }
}
