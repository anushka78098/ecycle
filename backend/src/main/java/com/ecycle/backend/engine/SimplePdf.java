package com.ecycle.backend.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal dependency-free PDF 1.4 writer: standard Helvetica / Helvetica-Bold / Courier text, lines, rectangles and QR codes drawn as vector squares.
 * It exists so certificates can be generated with no third-party PDF library. Text is limited to Latin-1 (other characters become '?').
 * Coordinates are in points, origin bottom-left.
 */
public final class SimplePdf {
    public enum Font { REGULAR("F1"), BOLD("F2"), MONO("F3"); final String res; Font(String r) { res = r; } }

    private static final int[] HELV = {278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278, 556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
            1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778, 667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
            333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556, 556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584};
    private static final int[] HELV_B = {278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278, 556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 333, 333, 584, 584, 584, 611,
            975, 722, 722, 722, 722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778, 667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 333, 278, 333, 584, 556,
            333, 556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556, 278, 889, 611, 611, 611, 611, 389, 556, 333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584};

    public static double width(String s, Font f, double size) {
        double w = 0;
        for (char c : s.toCharArray()) {
            if (f == Font.MONO) w += 600;
            else { int idx = c - 32; int[] t = f == Font.BOLD ? HELV_B : HELV; w += idx >= 0 && idx < t.length ? t[idx] : 556; }
        }
        return w * size / 1000.0;
    }

    /** Greedy word wrap using real glyph widths. */
    public static List<String> wrap(String text, Font f, double size, double maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String para : String.valueOf(text).split("\\R", -1)) {
            StringBuilder cur = new StringBuilder();
            for (String word : para.split(" ")) {
                String next = cur.length() == 0 ? word : cur + " " + word;
                if (width(next, f, size) <= maxWidth || cur.length() == 0) cur = new StringBuilder(next);
                else { lines.add(cur.toString()); cur = new StringBuilder(word); }
                while (width(cur.toString(), f, size) > maxWidth && cur.length() > 1) {   // a single token longer than the line (e.g. a hash): hard-split
                    int cut = cur.length() - 1; while (cut > 1 && width(cur.substring(0, cut), f, size) > maxWidth) cut--;
                    lines.add(cur.substring(0, cut)); cur = new StringBuilder(cur.substring(cut));
                }
            }
            lines.add(cur.toString());
        }
        return lines;
    }

    public final class Page {
        private final double w, h; private final StringBuilder ops = new StringBuilder();
        private Page(double w, double h) { this.w = w; this.h = h; }
        public double width() { return w; }
        public double height() { return h; }
        private static String n(double v) { return String.format(Locale.ROOT, "%.2f", v); }
        public Page color(double r, double g, double b) { ops.append(n(r)).append(' ').append(n(g)).append(' ').append(n(b)).append(" rg ").append(n(r)).append(' ').append(n(g)).append(' ').append(n(b)).append(" RG\n"); return this; }
        public Page text(double x, double y, Font f, double size, String s) {
            ops.append("BT /").append(f.res).append(' ').append(n(size)).append(" Tf ").append(n(x)).append(' ').append(n(y)).append(" Td (").append(escape(s)).append(") Tj ET\n"); return this;
        }
        public Page textCentered(double cx, double y, Font f, double size, String s) { return text(cx - SimplePdf.width(s, f, size) / 2, y, f, size, s); }
        /** Draws wrapped text; returns the y position below the last line. */
        public double paragraph(double x, double y, double maxWidth, Font f, double size, double leading, String s) {
            for (String line : wrap(s, f, size, maxWidth)) { text(x, y, f, size, line); y -= leading; }
            return y;
        }
        public Page line(double x1, double y1, double x2, double y2, double lw) { ops.append(n(lw)).append(" w ").append(n(x1)).append(' ').append(n(y1)).append(" m ").append(n(x2)).append(' ').append(n(y2)).append(" l S\n"); return this; }
        public Page strokeRect(double x, double y, double rw, double rh, double lw) { ops.append(n(lw)).append(" w ").append(n(x)).append(' ').append(n(y)).append(' ').append(n(rw)).append(' ').append(n(rh)).append(" re S\n"); return this; }
        public Page fillRect(double x, double y, double rw, double rh) { ops.append(n(x)).append(' ').append(n(y)).append(' ').append(n(rw)).append(' ').append(n(rh)).append(" re f\n"); return this; }
        /** Draws a QR module matrix (true = dark) with its lower-left corner at (x,y), merging horizontal runs into single rectangles. */
        public Page qr(boolean[][] m, double x, double y, double size) {
            int n = m.length; double cell = size / n;
            for (int r = 0; r < n; r++) {
                int c = 0;
                while (c < n) {
                    if (!m[r][c]) { c++; continue; }
                    int start = c; while (c < n && m[r][c]) c++;
                    fillRect(x + start * cell, y + size - (r + 1) * cell, (c - start) * cell, cell);
                }
            }
            return this;
        }
    }

    private final List<Page> pages = new ArrayList<>();
    private String title = "", author = "", subject = "", keywords = ""; private Instant created = Instant.now();
    public SimplePdf meta(String title, String author, String subject, String keywords, Instant created) { this.title = title; this.author = author; this.subject = subject; this.keywords = keywords; this.created = created; return this; }
    public Page addPage(double w, double h) { Page p = new Page(w, h); pages.add(p); return p; }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : String.valueOf(s).toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') sb.append('\\').append(c);
            else if (c >= 32 && c <= 126) sb.append(c);
            else if (c >= 160 && c <= 255) sb.append(String.format("\\%03o", (int) c));
            else sb.append('?');
        }
        return sb.toString();
    }

    public byte[] toBytes() {
        try {
            if (pages.isEmpty()) throw new IllegalStateException("A PDF needs at least one page");
            ByteArrayOutputStream out = new ByteArrayOutputStream(); List<Integer> offsets = new ArrayList<>();
            // object layout: 1 catalog, 2 pages, 3 info, 4-6 fonts, then (page, content) pairs
            int firstPageObj = 7;
            write(out, "%PDF-1.4\n%âãÏÓ\n");
            obj(out, offsets, 1, "<< /Type /Catalog /Pages 2 0 R >>");
            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < pages.size(); i++) kids.append(firstPageObj + 2 * i).append(" 0 R ");
            obj(out, offsets, 2, "<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>");
            String date = "D:" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC).format(created) + "Z";
            obj(out, offsets, 3, "<< /Title (" + escape(title) + ") /Author (" + escape(author) + ") /Subject (" + escape(subject) + ") /Keywords (" + escape(keywords) + ") /Producer (E-CYCLE) /CreationDate (" + date + ") >>");
            obj(out, offsets, 4, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
            obj(out, offsets, 5, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
            obj(out, offsets, 6, "<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>");
            for (int i = 0; i < pages.size(); i++) {
                Page p = pages.get(i); int po = firstPageObj + 2 * i, co = po + 1;
                obj(out, offsets, po, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + Page.n(p.w) + " " + Page.n(p.h) + "] /Resources << /Font << /F1 4 0 R /F2 5 0 R /F3 6 0 R >> >> /Contents " + co + " 0 R >>");
                byte[] content = p.ops.toString().getBytes(StandardCharsets.ISO_8859_1);
                offsets.add(out.size()); write(out, co + " 0 obj\n<< /Length " + content.length + " >>\nstream\n"); out.write(content); write(out, "\nendstream\nendobj\n");
            }
            int xref = out.size(); int total = 6 + 2 * pages.size();
            write(out, "xref\n0 " + (total + 1) + "\n0000000000 65535 f \n");
            for (int off : offsets) write(out, String.format(Locale.ROOT, "%010d 00000 n \n", off));
            write(out, "trailer\n<< /Size " + (total + 1) + " /Root 1 0 R /Info 3 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
            return out.toByteArray();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    private static void write(ByteArrayOutputStream o, String s) throws IOException { o.write(s.getBytes(StandardCharsets.ISO_8859_1)); }
    private static void obj(ByteArrayOutputStream o, List<Integer> offsets, int n, String body) throws IOException { offsets.add(o.size()); write(o, n + " 0 obj\n" + body + "\nendobj\n"); }
}
