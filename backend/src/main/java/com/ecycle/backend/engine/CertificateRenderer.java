package com.ecycle.backend.engine;

import com.ecycle.backend.engine.SimplePdf.Font;
import java.time.Instant;
import java.util.List;

/** Lays out a recycling / data-destruction certificate as a one-page A4 PDF using {@link SimplePdf}. Pure: all inputs are plain values. */
public final class CertificateRenderer {
    private CertificateRenderer() {}
    public record Input(String title, String certificateNo, String verificationCode, String issuedAt, List<String[]> rows, String statement, String contentHash,
                        String custodyAnchor, String verifyUrl, boolean[][] qr, String issuerLine) {}

    public static byte[] render(Input in, Instant created) {
        SimplePdf pdf = new SimplePdf().meta(in.title() + " " + in.certificateNo(), "E-CYCLE", in.title(), "certificate," + in.certificateNo(), created);
        SimplePdf.Page p = pdf.addPage(595, 842);
        double w = p.width();
        p.color(0.10, 0.45, 0.25).strokeRect(24, 24, w - 48, 794, 2.5).strokeRect(30, 30, w - 60, 782, 0.6);
        p.color(0.10, 0.45, 0.25).fillRect(30, 730, w - 60, 82);
        p.color(1, 1, 1).textCentered(w / 2, 782, Font.BOLD, 12, "E-CYCLE  |  CIRCULAR E-WASTE MARKETPLACE");
        p.textCentered(w / 2, 752, Font.BOLD, 22, in.title().toUpperCase());
        p.color(0.2, 0.2, 0.2).textCentered(w / 2, 705, Font.REGULAR, 11, "Certificate No.  " + in.certificateNo());
        p.textCentered(w / 2, 689, Font.REGULAR, 10, "Issued " + in.issuedAt());
        double y = 655;
        p.color(0, 0, 0);
        y = p.paragraph(60, y, w - 120, Font.REGULAR, 10.5, 14, in.statement()) - 8;
        p.color(0.75, 0.75, 0.75).line(60, y + 6, w - 60, y + 6, 0.5); y -= 10;
        for (String[] r : in.rows()) {
            if (r[1] == null || r[1].isBlank()) continue;
            p.color(0.35, 0.35, 0.35).text(60, y, Font.BOLD, 9.5, r[0]);
            p.color(0, 0, 0);
            double ny = p.paragraph(210, y, w - 270, Font.REGULAR, 9.5, 12.5, r[1]);
            y = Math.min(y - 14, ny) - 2;
        }
        p.color(0.75, 0.75, 0.75).line(60, y + 6, w - 60, y + 6, 0.5);
        // verification block
        double by = 215;
        p.color(0.10, 0.45, 0.25).text(60, by + 100, Font.BOLD, 10, "VERIFY THIS CERTIFICATE");
        p.color(0, 0, 0);
        p.text(60, by + 84, Font.REGULAR, 9, "Verification code:  " + in.verificationCode());
        p.paragraph(60, by + 70, 330, Font.REGULAR, 8, 10, "Scan the QR code, or open: " + in.verifyUrl());
        p.text(60, by + 38, Font.BOLD, 8, "SHA-256 of certificate content");
        p.paragraph(60, by + 28, 330, Font.MONO, 7, 9, in.contentHash());
        if (in.custodyAnchor() != null) { p.text(60, by - 2, Font.BOLD, 8, "Chain-of-custody anchor"); p.paragraph(60, by - 12, 330, Font.MONO, 7, 9, in.custodyAnchor()); }
        if (in.qr() != null) {
            p.color(1, 1, 1).fillRect(400, by - 4, 140, 140);
            p.color(0, 0, 0).qr(in.qr(), 410, by + 6, 120);
        }
        p.color(0.3, 0.3, 0.3).paragraph(60, 150, w - 120, Font.REGULAR, 8, 10.5, in.issuerLine());
        p.paragraph(60, 112, w - 120, Font.REGULAR, 7.5, 9.5, "This document is valid only if the verification code and content hash match the E-CYCLE record. Treatment weights marked 'measured' were recorded by the "
                + "issuing facility; carbon figures are estimates from configurable conversion factors. A printed or PDF copy is verified online, not by appearance alone.");
        return pdf.toBytes();
    }
}
