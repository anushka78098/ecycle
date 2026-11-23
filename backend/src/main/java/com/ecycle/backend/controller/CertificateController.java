package com.ecycle.backend.controller;

import com.ecycle.backend.config.Sec;
import com.ecycle.backend.service.*;
import com.ecycle.shared.Dtos.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class CertificateController {
    private final CertificateService certificates;

    @GetMapping("/api/certificates")
    public List<CertificateDto> mine() { return certificates.mine(Sec.me()); }
    @GetMapping("/api/certificates/listing/{listingId}")
    public List<CertificateDto> forListing(@PathVariable Long listingId) { return certificates.forListing(Sec.me(), listingId); }
    @GetMapping("/api/certificates/{no}")
    public CertificateDto get(@PathVariable String no) { return certificates.get(Sec.me(), no); }
    @GetMapping("/api/certificates/{no}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String no) {
        byte[] b = certificates.pdf(Sec.me(), no);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(no.replaceAll("[^A-Za-z0-9-]", "_") + ".pdf").build().toString())
                .header("X-Content-Type-Options", "nosniff").body(b);
    }

    // ---- public verification (no login): requires BOTH the number and the verification code printed on the certificate
    @GetMapping("/api/public/certificates/verify")
    public CertificateVerificationDto verify(@RequestParam String no, @RequestParam String code) { return certificates.verify(no, code); }
    @GetMapping(value = "/api/public/certificates/{no}/page", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@PathVariable String no, @RequestParam(defaultValue = "") String code) {
        return ResponseEntity.ok().header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'").header("X-Content-Type-Options", "nosniff").body(certificates.verificationPage(no, code));
    }
}
