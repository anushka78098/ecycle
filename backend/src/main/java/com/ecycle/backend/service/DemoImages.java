package com.ecycle.backend.service;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Draws product-style illustrations for demo listings and dispute evidence (PNG bytes, no external assets). */
final class DemoImages {
    private DemoImages() {}

    static byte[] device(String kind, String label, int seed, boolean cracked) {
        System.setProperty("java.awt.headless", "true");
        int W = 800, H = 600;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        float hue = ((seed * 47) % 360) / 360f;
        Color bg1 = Color.getHSBColor(hue, 0.22f, 0.96f), bg2 = Color.getHSBColor((hue + 0.08f) % 1f, 0.38f, 0.82f);
        Color accent = Color.getHSBColor(hue, 0.60f, 0.80f), dark = Color.getHSBColor(hue, 0.45f, 0.20f);
        g.setPaint(new GradientPaint(0, 0, bg1, W, H, bg2)); g.fillRect(0, 0, W, H);
        g.setColor(new Color(255, 255, 255, 60));
        for (int x = 0; x < W; x += 40) g.drawLine(x, 0, x, H);
        for (int y = 0; y < H; y += 40) g.drawLine(0, y, W, y);
        g.setColor(new Color(0, 0, 0, 40)); g.fillOval(190, 478, 420, 46);           // soft shadow
        Rectangle screen = null;
        switch (kind) {
            case "phone" -> { g.setColor(dark); g.fill(new RoundRectangle2D.Double(285, 50, 230, 470, 36, 36)); screen = new Rectangle(300, 90, 200, 380); g.setColor(accent); g.fill(screen); g.setColor(new Color(255, 255, 255, 120)); g.fillOval(388, 485, 24, 24); }
            case "tablet" -> { g.setColor(dark); g.fill(new RoundRectangle2D.Double(190, 70, 420, 440, 30, 30)); screen = new Rectangle(212, 92, 376, 396); g.setColor(accent); g.fill(screen); }
            case "laptop" -> { g.setColor(dark); g.fill(new RoundRectangle2D.Double(165, 110, 470, 300, 20, 20)); screen = new Rectangle(182, 126, 436, 268); g.setColor(accent); g.fill(screen);
                g.setColor(dark); g.fillPolygon(new int[]{110, 690, 650, 150}, new int[]{410, 410, 460, 460}, 4); }
            case "monitor" -> { g.setColor(dark); g.fill(new RoundRectangle2D.Double(130, 80, 540, 340, 18, 18)); screen = new Rectangle(146, 96, 508, 292); g.setColor(accent); g.fill(screen);
                g.setColor(dark); g.fillRect(375, 420, 50, 60); g.fillRoundRect(300, 476, 200, 20, 10, 10); }
            case "board" -> { g.setColor(new Color(24, 120, 76)); g.fill(new RoundRectangle2D.Double(130, 90, 540, 400, 14, 14));
                g.setColor(new Color(232, 200, 90)); g.setStroke(new BasicStroke(3));
                for (int i = 0; i < 9; i++) { g.drawLine(160, 130 + i * 38, 300 + (i % 3) * 60, 130 + i * 38); g.drawLine(500, 130 + i * 38, 640, 130 + i * 38); }
                g.setColor(dark); g.fillRect(320, 170, 150, 150); g.setColor(accent); g.fillRect(340, 190, 110, 110);
                g.setColor(new Color(30, 30, 30)); g.fillRect(200, 390, 90, 50); g.fillRect(520, 380, 110, 60);
                g.setColor(new Color(200, 200, 200)); for (int i = 0; i < 6; i++) g.fillRect(325 + i * 24, 345, 14, 30); }
            case "battery" -> { g.setColor(dark); g.fill(new RoundRectangle2D.Double(150, 170, 480, 250, 40, 40)); g.fillRect(630, 250, 40, 90);
                g.setColor(accent); g.fill(new RoundRectangle2D.Double(170, 190, 440, 210, 28, 28));
                g.setColor(Color.WHITE); g.fillPolygon(new int[]{400, 330, 385, 345, 450, 395, 430}, new int[]{205, 310, 310, 385, 275, 275, 205}, 7); }
            default -> { g.setColor(dark); g.fill(new RoundRectangle2D.Double(150, 130, 500, 330, 30, 30));
                g.setColor(accent); g.fillOval(325, 190, 150, 150); g.setColor(new Color(255, 255, 255, 110)); g.fillOval(365, 230, 70, 70);
                g.setColor(accent); g.fillRoundRect(180, 160, 90, 14, 8, 8); g.setColor(new Color(255, 255, 255, 90)); g.fillRect(180, 400, 440, 12); }
        }
        if (cracked && screen != null) {
            g.setColor(new Color(255, 255, 255, 230)); g.setStroke(new BasicStroke(3f));
            int cx = screen.x + screen.width / 2, cy = screen.y + screen.height / 2;
            int[][] pts = {{-90, -120}, {60, -140}, {140, -20}, {70, 130}, {-60, 110}, {-130, 20}};
            for (int[] p : pts) { g.drawLine(cx, cy, cx + p[0], cy + p[1]); g.drawLine(cx + p[0], cy + p[1], cx + p[0] + 25, cy + p[1] + 38); }
        }
        g.setColor(dark); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        String t = label.length() > 34 ? label.substring(0, 33) + "..." : label;
        g.drawString(t, (W - g.getFontMetrics().stringWidth(t)) / 2, 556);
        g.setColor(new Color(0, 0, 0, 90)); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15)); g.drawString("E-CYCLE demo image", 18, 28);
        g.setColor(Color.WHITE); g.fillOval(752, 18, 22, 22); g.setColor(new Color(0, 0, 0, 70)); g.drawOval(752, 18, 22, 22);   // asset-tag hole
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) { ImageIO.write(img, "png", out); return out.toByteArray(); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }
}
