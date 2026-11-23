import com.ecycle.backend.engine.*;
import com.ecycle.shared.Enums.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Dependency-free checks of the engines behind the five upgrade features (hash chain, certificates, geo, pickup flow, eco-points, wipe checklists, PDF). */
public class CheckFeatures {
  static int fails = 0;
  static void ok(boolean c, String m) { if (!c) { fails++; System.out.println("FAIL: " + m); } else System.out.println("ok   : " + m); }

  static HashChain.Entry entry(String asset, int seq, String type, String detail, long at, String prev) {
    HashChain.Entry draft = new HashChain.Entry(asset, seq, type, 7L, "SELLER", 7L, "Ludhiana", detail, at, prev, "");
    return new HashChain.Entry(asset, seq, type, 7L, "SELLER", 7L, "Ludhiana", detail, at, prev, HashChain.hashOf(draft));
  }
  static List<HashChain.Entry> chain(int n) {
    List<HashChain.Entry> l = new ArrayList<>(); String prev = HashChain.GENESIS;
    for (int i = 1; i <= n; i++) { HashChain.Entry e = entry("EW-1", i, i == 1 ? "CREATED" : "HANDOVER", "step " + i, 1_700_000_000_000L + i * 1000L, prev); l.add(e); prev = e.hash(); }
    return l;
  }
  static HashChain.Entry with(HashChain.Entry e, String detail) {
    return new HashChain.Entry(e.assetCode(), e.seq(), e.eventType(), e.actorId(), e.actorRole(), e.custodianId(), e.location(), detail, e.occurredAtMillis(), e.prevHash(), e.hash());
  }

  public static void main(String[] a) throws Exception {
    // ---- hash chain
    var c = chain(5);
    ok(HashChain.verify(c).valid() && HashChain.verify(c).checked() == 5, "untouched chain verifies");
    ok(HashChain.verify(List.of()).valid(), "empty chain verifies");
    var edited = new ArrayList<>(c); edited.set(2, with(c.get(2), "tampered"));
    var v = HashChain.verify(edited); ok(!v.valid() && v.brokenAtSeq() == 3, "edited entry detected at its sequence number (3)");
    var removed = new ArrayList<>(c); removed.remove(1); v = HashChain.verify(removed); ok(!v.valid(), "deleted middle entry detected");
    var swapped = new ArrayList<>(c); Collections.swap(swapped, 1, 2); ok(!HashChain.verify(swapped).valid(), "re-ordered entries detected");
    var rehashed = new ArrayList<>(c); HashChain.Entry t = with(c.get(1), "forged");
    rehashed.set(1, new HashChain.Entry(t.assetCode(), t.seq(), t.eventType(), t.actorId(), t.actorRole(), t.custodianId(), t.location(), t.detail(), t.occurredAtMillis(), t.prevHash(), HashChain.hashOf(t)));
    ok(!HashChain.verify(rehashed).valid(), "forged entry with recomputed own hash still breaks the next link");
    ok(HashChain.verify(c.subList(0, 3)).valid() && !HashChain.verify(c.subList(0, 3)).headHash().equals(c.get(4).hash()), "tail truncation is NOT detectable by the chain alone (head hash differs -> certificate anchor catches it)");
    ok(!HashChain.hashOf(entry("A|B", 1, "X", "d", 1L, HashChain.GENESIS)).equals(HashChain.hashOf(entry("A", 1, "B|X", "d", 1L, HashChain.GENESIS))), "length-prefixed canonical form: field-boundary shifting does not collide");
    ok(HashChain.sha256Hex("abc").equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), "SHA-256 known-answer test");

    // ---- certificates
    Map<String, String> p = new TreeMap<>(Map.of("items", "Laptop", "weightKg", "1.6", "holder", "A"));
    String h = CertificateEngine.contentHash(p), code = CertificateEngine.newVerificationCode(), sig = CertificateEngine.sign("secret-key-1", "REC-1", code, h);
    ok(code.matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}"), "verification code format " + code);
    ok(CertificateEngine.verify("secret-key-1", "REC-1", code, p, h, sig), "genuine certificate verifies");
    Map<String, String> q = new TreeMap<>(p); q.put("weightKg", "16");
    ok(!CertificateEngine.verify("secret-key-1", "REC-1", code, q, h, sig), "changed payload rejected");
    ok(!CertificateEngine.verify("other-secret", "REC-1", code, p, h, sig), "wrong signing key rejected");
    ok(!CertificateEngine.verify("secret-key-1", "REC-2", code, p, h, sig), "changed number rejected");
    ok(!CertificateEngine.verify("secret-key-1", "REC-1", "AAAA-AAAA-AAAA", p, h, sig), "changed verification code rejected");
    ok(CertificateEngine.contentHash(new LinkedHashMap<>(p)).equals(h), "content hash independent of map order");
    Set<String> codes = new HashSet<>(); for (int i = 0; i < 500; i++) codes.add(CertificateEngine.newVerificationCode()); ok(codes.size() == 500, "500 verification codes are unique");

    // ---- geo
    double d = GeoMath.haversineKm(28.6139, 77.2090, 19.0760, 72.8777);
    ok(Math.abs(d - 1153) < 15, "Delhi-Mumbai great-circle distance " + String.format("%.0f", d) + " km (~1150)");
    ok(GeoMath.haversineKm(30.9, 75.85, 30.9, 75.85) == 0, "zero distance");
    double[] bb = GeoMath.boundingBox(30.9, 75.85, 50); ok(bb[0] < 30.9 && bb[1] > 30.9 && bb[2] < 75.85 && bb[3] > 75.85, "bounding box contains the centre");
    ok(GeoMath.haversineKm(30.9, 75.85, bb[1], 75.85) >= 49.9, "box edge is at least the radius away (never excludes in-radius points)");
    ok(GeoMath.validLatLon(0.0, 0.0) && !GeoMath.validLatLon(91.0, 0.0) && !GeoMath.validLatLon(0.0, 181.0) && !GeoMath.validLatLon(null, 1.0), "lat/lon validation");

    // ---- pickup flow
    var A = PickupFlow.Actor.class; var R = PickupFlow.Actor.RECYCLER; var Q = PickupFlow.Actor.REQUESTER; var AD = PickupFlow.Actor.ADMIN;
    ok(PickupFlow.canTransition(PickupStatus.REQUESTED, PickupStatus.ACCEPTED, R) && !PickupFlow.canTransition(PickupStatus.REQUESTED, PickupStatus.ACCEPTED, Q), "only the recycler accepts");
    ok(PickupFlow.canTransition(PickupStatus.REQUESTED, PickupStatus.CANCELLED, Q) && !PickupFlow.canTransition(PickupStatus.PICKED_UP, PickupStatus.CANCELLED, Q), "requester can cancel only before pickup");
    ok(!PickupFlow.canTransition(PickupStatus.REQUESTED, PickupStatus.PICKED_UP, R), "cannot skip states");
    ok(PickupFlow.terminal(PickupStatus.COMPLETED) && PickupFlow.allowedNext(PickupStatus.COMPLETED, R).isEmpty() && PickupFlow.allowedNext(PickupStatus.CANCELLED, AD).isEmpty(), "terminal states have no exits");
    PickupStatus s = PickupStatus.REQUESTED; for (PickupStatus n : List.of(PickupStatus.ACCEPTED, PickupStatus.EN_ROUTE, PickupStatus.PICKED_UP, PickupStatus.AT_FACILITY, PickupStatus.COMPLETED)) { ok(PickupFlow.canTransition(s, n, R), "happy path " + s + " -> " + n); s = n; }
    ok(PickupFlow.recyclerHoldsCustody(PickupStatus.PICKED_UP) && !PickupFlow.recyclerHoldsCustody(PickupStatus.EN_ROUTE), "custody passes at pickup, not before");

    // ---- eco engine
    ok(EcoEngine.level(0) == 1 && EcoEngine.level(100) == 2 && EcoEngine.level(399) == 2 && EcoEngine.level(400) == 3, "level thresholds 0/100/400");
    ok(EcoEngine.pointsForLevel(3) == 400 && EcoEngine.levelProgress(250) > 0.0 && EcoEngine.levelProgress(250) < 1.0, "level progress between 0 and 1");
    ok(EcoEngine.pointsForWeight(6, 2.5) == 15 && EcoEngine.pointsForWeight(6, 0.01) == 1 && EcoEngine.pointsForWeight(0, 5) == 0 && EcoEngine.pointsForWeight(6, -1) == 0, "points per kg (min 1 for positive weight)");
    var eq = EcoEngine.equivalents(42); ok(Math.abs(eq.treeYears() - 2.0) < 1e-9 && Math.abs(eq.carKm() - 168) < 1e-9, "42 kg CO2e = 2 tree-years = 168 km");
    ok(EcoEngine.challengeFraction(5, 10) == 0.5 && EcoEngine.challengeFraction(15, 10) == 1 && EcoEngine.challengeFraction(1, 0) == 1, "challenge fraction clamps to 0..1");
    var ranks = EcoEngine.rank(List.of(300L, 300L, 200L, 100L)); ok(ranks.get(1).rank() == 1 && ranks.get(2).rank() == 3 && ranks.get(3).rank() == 4, "ties share a rank (1,1,3,4)");
    ok(EcoEngine.publicName("Asha Kumar").equals("Asha K.") && EcoEngine.publicName(" ").equals("Eco member") && EcoEngine.publicName("Madonna").equals("Madonna"), "leaderboard hides surnames");

    // ---- wipe checklists
    var phone = WipeChecklists.forCategory("Smartphones"); var lap = WipeChecklists.forCategory("Laptops");
    ok(!phone.isEmpty() && !lap.isEmpty() && phone.stream().anyMatch(WipeChecklists.Item::mandatory), "templates exist with mandatory steps");
    List<String> all = lap.stream().map(WipeChecklists.Item::code).toList();
    ok(WipeChecklists.missingMandatory("Laptops", all).isEmpty(), "all steps done -> nothing missing");
    ok(!WipeChecklists.missingMandatory("Laptops", List.of()).isEmpty(), "no steps done -> mandatory missing");
    ok(WipeChecklists.unknownCodes("Laptops", List.of("NOPE")).equals(List.of("NOPE")), "unknown step codes reported");
    ok(WipeChecklists.knownMethod("NIST 800-88 Purge") && !WipeChecklists.knownMethod("rm -rf"), "method whitelist");
    ok(!WipeChecklists.forCategory(null).isEmpty() && !WipeChecklists.forCategory("Something else").isEmpty(), "generic template for unknown/null category");

    // ---- PDF
    boolean[][] qr = new boolean[25][25]; for (int i = 0; i < 25; i++) for (int j = 0; j < 25; j++) qr[i][j] = (i * j + i) % 3 == 0;
    byte[] pdf = CertificateRenderer.render(new CertificateRenderer.Input("Certificate of Recycling", "REC-20261118-ABC234", "ABCD-2345-WXYZ", "2026-11-18 10:00 UTC",
        List.of(new String[]{"Issued to", "Asha (test)"}, new String[]{"Equipment", "Laptop (parentheses) \\ backslash"}, new String[]{"Missing", null}), "This certifies that the equipment was recycled.",
        h, "entry #4  " + c.get(3).hash(), "http://localhost:8080/api/public/certificates/REC-1/page?code=X", qr, "Issued by: Test"), Instant.parse("2026-11-18T10:00:00Z"));
    String text = new String(pdf, StandardCharsets.ISO_8859_1);
    ok(text.startsWith("%PDF-1.4") && text.trim().endsWith("%%EOF"), "PDF header/trailer");
    ok(pdf.length > 1500 && text.contains("/Type /Catalog") && text.contains("xref"), "PDF has catalog and xref (" + pdf.length + " bytes)");
    java.nio.file.Files.write(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "ecycle-check-certificate.pdf"), pdf);
    ok(text.contains("\\(parentheses\\)") && text.contains("\\\\ backslash"), "PDF text is escaped");

    System.out.println(fails == 0 ? "ALL FEATURE ENGINE CHECKS PASSED" : fails + " FAILURES"); System.exit(fails);
  }
}
