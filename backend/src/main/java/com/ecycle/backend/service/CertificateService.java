package com.ecycle.backend.service;

import com.ecycle.backend.config.AppProps;
import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.engine.CertificateEngine;
import com.ecycle.backend.engine.CertificateRenderer;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifiable PDF certificates (data sanitization, data destruction, recycling). The signed facts are stored immutably in PostgreSQL; the PDF is rendered on demand from that record,
 * so a download can never differ from what was issued. Reuses the existing sanitization records / DSC numbers, the passport QR and the custody chain.
 */
@Service @RequiredArgsConstructor
public class CertificateService {
    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    /** Fields shown to anyone who has the certificate number + verification code. No personal data. */
    private static final Set<String> PUBLIC_KEYS = Set.of("items", "quantity", "weightKg", "treatment", "facility", "dataDestructionMethod", "method", "passportCode", "issuedAt", "device");
    private final CertificateRepository certs; private final SanitizationRecordRepository records; private final ListingRepository listings; private final UserRepository users;
    private final CustodyService custody; private final Lookup lookup; private final NotificationService notifications; private final AuditService audit; private final AppProps props;
    private final ObjectMapper mapper = new ObjectMapper(); private final SecureRandom rnd = new SecureRandom(); private String secret;

    @PostConstruct void init() {
        String s = props.getCertificateSigningSecret();
        if (s == null || s.isBlank()) {
            String jwt = props.getJwtSecret();
            if (jwt == null || jwt.isBlank()) jwt = "ecycle-dev-only-certificate-key";   // dev profile without a JWT secret: certificates will not survive restarts of a rotated secret
            s = CertificateEngine.sign(jwt, "derived", "ecycle-certificate-key-v1", "x");
            log.warn("app.certificate.signing-secret is not set; deriving the certificate signing key from the JWT secret. Set CERT_SIGNING_SECRET so rotating JWT_SECRET does not invalidate certificates.");
        }
        this.secret = s;
    }

    // ------------------------------------------------------------------ issuing
    private String newNumber(String prefix) {
        String no;
        do { StringBuilder sb = new StringBuilder(prefix).append('-').append(LocalDate.now(ZoneOffset.UTC).toString().replace("-", "")).append('-'); for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length()))); no = sb.toString(); }
        while (certs.findByCertificateNo(no).isPresent());
        return no;
    }

    @Transactional
    public Certificate issue(CertificateType type, String certNo, Long holderId, Long issuerId, Long listingId, Long pickupId, Long recordId, String passportCode, Map<String, String> details) {
        String no = certNo != null ? certNo : newNumber(switch (type) { case RECYCLING -> "REC"; case DATA_DESTRUCTION -> "DDC"; case DATA_SANITIZATION -> "DSC"; });
        String code = CertificateEngine.newVerificationCode();
        Instant now = Instant.now();
        Map<String, String> payload = new TreeMap<>(details);
        payload.put("type", type.name()); payload.put("certificateNo", no); payload.put("issuedAt", STAMP.format(now)); payload.put("holder", lookup.name(holderId)); payload.put("issuer", lookup.name(issuerId));
        if (passportCode != null) payload.put("passportCode", passportCode);
        CustodyEvent head = null;
        if (passportCode != null) {
            try { head = custody.recordSystem(passportCode, "CERTIFICATE_ISSUED", issuerId, null, null, type + " certificate " + no); }
            catch (RuntimeException e) { log.warn("Could not extend custody chain for {}: {}", passportCode, e.getMessage()); }
        }
        if (head != null) { payload.put("custodySeq", String.valueOf(head.getSeq())); payload.put("custodyHead", head.getHash()); }
        Certificate c = new Certificate();
        c.setCertificateNo(no); c.setType(type); c.setVerificationCode(code); c.setListingId(listingId); c.setPickupId(pickupId); c.setSanitizationRecordId(recordId); c.setPassportCode(passportCode);
        c.setHolderId(holderId); c.setIssuedBy(issuerId); c.setIssuedAt(now.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        c.setPayload(toJson(payload)); c.setContentHash(CertificateEngine.contentHash(payload)); c.setSignature(CertificateEngine.sign(secret, no, code, c.getContentHash()));
        if (head != null) { c.setCustodySeq(head.getSeq()); c.setCustodyHeadHash(head.getHash()); }
        certs.save(c);
        audit.log(issuerId, "CERTIFICATE_ISSUED", "certificates", c.getId(), type + " " + no);
        notifications.notify(holderId, type.name().replace('_', ' ').toLowerCase(Locale.ROOT) + " certificate issued", "Certificate " + no + " is ready to download from the Certificates screen.", "CERTIFICATE");
        return c;
    }

    /** Called when an administrator verifies sanitization evidence (existing flow). The DSC number is reused as the certificate number. */
    @Transactional
    public void issueForSanitization(String certificateNo, Long adminId) {
        SanitizationRecord r = records.findByCertificateNo(certificateNo).orElse(null);
        if (r == null || certs.findBySanitizationRecordId(r.getId()).isPresent()) return;
        Listing l = listings.findById(r.getListingId()).orElse(null);
        if (l == null) return;
        Map<String, String> d = new TreeMap<>();
        d.put("items", l.getName() + (l.getQuantity() > 1 ? " x" + l.getQuantity() : "")); d.put("device", join(l.getBrand(), l.getModel()));
        d.put("method", r.getMethod()); d.put("tool", r.getToolName()); d.put("evidenceRef", r.getEvidenceRef()); d.put("verification", "Evidence reviewed and approved by an E-CYCLE administrator" + (r.getNote() == null ? "" : ": " + r.getNote()));
        issue(CertificateType.DATA_SANITIZATION, certificateNo, r.getSubmittedBy(), adminId, l.getId(), null, r.getId(), passportOf(l.getId()), d);
    }
    private String passportOf(Long listingId) { return custody.passportCodeForListing(listingId); }
    private static String join(String a, String b) { return ((a == null ? "" : a) + " " + (b == null ? "" : b)).trim(); }

    // ------------------------------------------------------------------ reading
    @Transactional(readOnly = true)
    public List<CertificateDto> mine(AuthUser me) {
        List<Certificate> l = me.has(Role.RESOLVER) ? certs.findTop300ByOrderByIdDesc() : certs.findByHolderIdOrderByIdDesc(me.id());
        if (!me.has(Role.RESOLVER)) { Set<Long> have = new HashSet<>(); l.forEach(c -> have.add(c.getId())); /* certificates issued BY this user (recycler) */ certs.findTop300ByOrderByIdDesc().stream().filter(c -> c.getIssuedBy().equals(me.id()) && !have.contains(c.getId())).forEach(l::add); }
        return l.stream().sorted(Comparator.comparing(Certificate::getId).reversed()).map(this::dto).toList();
    }
    @Transactional(readOnly = true)
    public List<CertificateDto> forListing(AuthUser me, Long listingId) {
        Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing not found"));
        return certs.findByListingIdOrderByIdDesc(listingId).stream().filter(c -> canView(me, c)).map(this::dto).toList();
    }
    @Transactional(readOnly = true)
    public CertificateDto get(AuthUser me, String no) { return dto(load(me, no)); }
    private Certificate load(AuthUser me, String no) {
        Certificate c = certs.findByCertificateNo(no).orElseThrow(() -> ApiException.notFound("Certificate not found"));
        if (!canView(me, c)) throw ApiException.notFound("Certificate not found");
        return c;
    }
    private boolean canView(AuthUser me, Certificate c) { return me.has(Role.RESOLVER) || me.id().equals(c.getHolderId()) || me.id().equals(c.getIssuedBy()); }

    private Map<String, String> payload(Certificate c) {
        try { return mapper.readValue(c.getPayload(), new TypeReference<TreeMap<String, String>>() {}); } catch (Exception e) { throw new IllegalStateException("Corrupt certificate payload", e); }
    }
    private String toJson(Map<String, String> m) { try { return mapper.writeValueAsString(m); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String verifyUrl(Certificate c) { return props.getPublicBaseUrl().replaceAll("/+$", "") + "/api/public/certificates/" + c.getCertificateNo() + "/page?code=" + c.getVerificationCode(); }
    private CertificateDto dto(Certificate c) {
        return new CertificateDto(c.getId(), c.getCertificateNo(), c.getType(), c.getVerificationCode(), c.getListingId(), c.getPickupId(), c.getPassportCode(), lookup.name(c.getHolderId()),
                lookup.name(c.getIssuedBy()), payload(c), c.getContentHash(), c.getCustodySeq(), c.getCustodyHeadHash(), verifyUrl(c), c.getIssuedAt());
    }

    // ------------------------------------------------------------------ public verification
    @Transactional(readOnly = true)
    public CertificateVerificationDto verify(String no, String code) {
        Certificate c = certs.findByCertificateNo(no == null ? "" : no.trim().toUpperCase(Locale.ROOT)).orElse(null);
        boolean codeOk = c != null && code != null && MessageDigest.isEqual(c.getVerificationCode().getBytes(StandardCharsets.UTF_8), code.trim().toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        if (!codeOk) return new CertificateVerificationDto(false, "No certificate matches this number and verification code.", null, null, null, null, Map.of(), false);
        Map<String, String> p = payload(c);
        boolean sigOk = CertificateEngine.verify(secret, c.getCertificateNo(), c.getVerificationCode(), p, c.getContentHash(), c.getSignature());
        boolean anchor = c.getPassportCode() == null || custody.anchorIntact(c.getPassportCode(), c.getCustodySeq(), c.getCustodyHeadHash());
        Map<String, String> pub = new LinkedHashMap<>(); p.entrySet().stream().filter(e -> PUBLIC_KEYS.contains(e.getKey())).forEach(e -> pub.put(e.getKey(), e.getValue()));
        String msg = !sigOk ? "INVALID: the stored certificate does not match its signature." : !anchor ? "Signature valid, but the chain of custody recorded at issuance no longer verifies - treat with caution." : "Valid: issued by E-CYCLE and unaltered.";
        return new CertificateVerificationDto(sigOk, msg, c.getCertificateNo(), c.getType(), c.getIssuedAt(), p.get("issuer"), pub, anchor);
    }

    // ------------------------------------------------------------------ PDF
    @Transactional(readOnly = true)
    public byte[] pdf(AuthUser me, String no) { return render(load(me, no)); }

    byte[] render(Certificate c) {
        Map<String, String> p = payload(c);
        String title = switch (c.getType()) { case RECYCLING -> "Certificate of Recycling"; case DATA_DESTRUCTION -> "Certificate of Data Destruction"; case DATA_SANITIZATION -> "Certificate of Data Sanitization"; };
        String statement = switch (c.getType()) {
            case RECYCLING -> "This certifies that the electronic equipment described below was received by the named recycling facility and processed through environmentally sound recycling and material recovery, as verified by the facility on the date of issue.";
            case DATA_DESTRUCTION -> "This certifies that all user data on the storage media of the equipment described below was irreversibly destroyed or sanitized by the named facility using the stated method, and that the result was verified before the equipment was processed.";
            case DATA_SANITIZATION -> "This certifies that the storage of the device described below was sanitized using the stated method and that the supplied erase evidence was reviewed and approved by an E-CYCLE administrator.";
        };
        List<String[]> rows = new ArrayList<>();
        String[][] fields = {{"Issued to", "holder"}, {"Equipment", "items"}, {"Device", "device"}, {"Quantity", "quantity"}, {"Verified weight (kg)", "weightKg"}, {"Treatment", "treatment"},
                {"Sanitization method", "dataDestructionMethod"}, {"Sanitization method", "method"}, {"Tool", "tool"}, {"Device serial", "serial"}, {"Evidence reference", "evidenceRef"},
                {"Recycling facility", "facility"}, {"Facility address", "facilityAddress"}, {"Pickup tracking no.", "pickup"}, {"Asset passport", "passportCode"}, {"Estimated CO2e avoided (kg)", "carbonAvoidedKg"},
                {"Verification", "verification"}, {"Verified by", "issuer"}};
        for (String[] f : fields) rows.add(new String[]{f[0], p.get(f[1])});
        String anchor = c.getCustodyHeadHash() == null ? null : "entry #" + c.getCustodySeq() + "  " + c.getCustodyHeadHash();
        return CertificateRenderer.render(new CertificateRenderer.Input(title, c.getCertificateNo(), c.getVerificationCode(), STAMP.format(c.getIssuedAt()), rows, statement, c.getContentHash(), anchor,
                verifyUrl(c), qrMatrix(verifyUrl(c)), "Issued by: " + p.getOrDefault("issuer", "E-CYCLE") + " (" + c.getType().name().replace('_', ' ') + ")"), c.getIssuedAt());
    }
    private static boolean[][] qrMatrix(String text) {
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, Map.of(EncodeHintType.MARGIN, 0));
            boolean[][] out = new boolean[m.getHeight()][m.getWidth()];
            for (int y = 0; y < m.getHeight(); y++) for (int x = 0; x < m.getWidth(); x++) out[y][x] = m.get(x, y);
            return out;
        } catch (Exception e) { throw new IllegalStateException("QR generation failed", e); }
    }

    /** Minimal HTML page opened when a phone scans the certificate QR code. All values are HTML-escaped. */
    public String verificationPage(String no, String code) {
        CertificateVerificationDto v = verify(no, code);
        StringBuilder sb = new StringBuilder("<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>E-CYCLE certificate check</title>")
                .append("<style>body{font-family:sans-serif;max-width:560px;margin:24px auto;padding:0 16px}.ok{color:#176b34}.bad{color:#a11}td{padding:4px 12px 4px 0;vertical-align:top}</style></head><body>")
                .append("<h2 class='").append(v.valid() ? "ok" : "bad").append("'>").append(v.valid() ? "Certificate verified" : "Not verified").append("</h2><p>").append(esc(v.message())).append("</p>");
        if (v.certificateNo() != null) {
            sb.append("<table><tr><td><b>Number</b></td><td>").append(esc(v.certificateNo())).append("</td></tr><tr><td><b>Type</b></td><td>").append(esc(v.type().name())).append("</td></tr><tr><td><b>Issued</b></td><td>").append(esc(String.valueOf(v.issuedAt()))).append("</td></tr>");
            v.publicDetails().forEach((k, val) -> sb.append("<tr><td><b>").append(esc(k)).append("</b></td><td>").append(esc(val)).append("</td></tr>"));
            sb.append("</table>");
        }
        return sb.append("</body></html>").toString();
    }
    private static String esc(String s) { return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
}
