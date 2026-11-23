package com.ecycle.frontend.component;

import com.ecycle.frontend.navigation.Nav;
import com.ecycle.frontend.util.Alerts;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Swing is used here deliberately: the Java 2D printing API (java.awt.print) renders passport QR labels and sanitization certificates
 * to a real printer. The Swing panel is hosted inside JavaFX through SwingNode and created/updated only on the Swing EDT.
 */
public final class SwingPrint {
    private SwingPrint() {}

    /** Printable label: optional QR image on the left, text lines on the right. */
    static final class LabelPanel extends JPanel {
        private final BufferedImage qr; private final String heading; private final String[] lines;
        LabelPanel(BufferedImage qr, String heading, String[] lines) { this.qr = qr; this.heading = heading; this.lines = lines; setPreferredSize(new Dimension(520, 260)); setBackground(Color.WHITE); }
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create(); g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0x14532d)); g.fillRect(0, 0, getWidth(), 34); g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 16)); g.drawString(heading, 12, 23);
            int x = 12;
            if (qr != null) { g.drawImage(qr, 12, 46, 190, 190, null); x = 216; }
            g.setColor(Color.BLACK); g.setFont(new Font("SansSerif", Font.PLAIN, 13)); int y = 62;
            for (String l : lines) { for (String part : wrap(l, g.getFontMetrics(), getWidth() - x - 12)) { g.drawString(part, x, y); y += 18; } y += 4; }
            g.setColor(new Color(0x9ca3af)); g.drawRect(0, 0, getWidth() - 1, getHeight() - 1); g.dispose();
        }
        private static java.util.List<String> wrap(String s, FontMetrics fm, int w) {
            java.util.List<String> out = new java.util.ArrayList<>(); StringBuilder cur = new StringBuilder();
            for (String word : s.split(" ")) { if (fm.stringWidth(cur + " " + word) > w && cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); } cur.append(cur.length() == 0 ? "" : " ").append(word); }
            if (cur.length() > 0) out.add(cur.toString()); return out;
        }
    }

    public static void preview(String title, String heading, BufferedImage qr, String... lines) {
        SwingNode node = new SwingNode(); LabelPanel[] holder = new LabelPanel[1];
        SwingUtilities.invokeLater(() -> { holder[0] = new LabelPanel(qr, heading, lines); node.setContent(holder[0]); });
        Dialog<ButtonType> d = new Dialog<>(); d.setTitle(title); d.setHeaderText(title); d.getDialogPane().getStylesheets().add(Nav.css());
        Button print = UI.primary("Print...", () -> print(holder[0]));
        VBox box = new VBox(12, new StackPane(node), print); box.setPadding(new Insets(10)); node.setStyle("-fx-border-color: #d1d5db;");
        d.getDialogPane().setContent(box); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.getDialogPane().setMinWidth(580); d.show();
    }

    private static void print(LabelPanel panel) {
        if (panel == null) return;
        Thread t = new Thread(() -> {
            try {
                PrinterJob job = PrinterJob.getPrinterJob();
                job.setPrintable((Graphics g, PageFormat pf, int page) -> {
                    if (page > 0) return Printable.NO_SUCH_PAGE;
                    Graphics2D g2 = (Graphics2D) g; g2.translate(pf.getImageableX(), pf.getImageableY()); panel.printAll(g2); return Printable.PAGE_EXISTS;
                });
                if (job.printDialog()) job.print();
            } catch (PrinterException e) { Platform.runLater(() -> Alerts.error(e)); }
        }, "ecycle-print");
        t.setDaemon(true); t.start();
    }
}
