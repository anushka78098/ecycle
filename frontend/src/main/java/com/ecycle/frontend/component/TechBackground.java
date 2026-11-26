package com.ecycle.frontend.component;

import java.util.Random;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

/**
 * Animated dark-blue "tech" backdrop: a drifting grid, a scanning light band, pulsing teal glows, a particle network
 * and (unless lite) floating tech tiles. Runs only while attached to a showing scene.
 */
public class TechBackground extends Pane {
    private static final String[] TILE_TEXT = {"JAVA", "</>", "RECYCLE", "AI", "CO2", "PCB", "SQL", "BATT", "API", "JWT"};
    private static final int PARTICLES = 55;

    private final Canvas canvas = new Canvas();
    private final Pane tiles = new Pane();
    private final Random rnd = new Random(11);
    private final double[] px = new double[PARTICLES], py = new double[PARTICLES], pvx = new double[PARTICLES], pvy = new double[PARTICLES];
    private final Label[] tile = new Label[TILE_TEXT.length];
    private final double[] tbx = new double[TILE_TEXT.length], tby = new double[TILE_TEXT.length], tax = new double[TILE_TEXT.length],
            tay = new double[TILE_TEXT.length], tsp = new double[TILE_TEXT.length], tph = new double[TILE_TEXT.length];
    private boolean lite;
    private final AnimationTimer timer;

    public TechBackground() {
        getStyleClass().add("tech-bg");
        setMouseTransparent(true);
        canvas.setMouseTransparent(true); tiles.setMouseTransparent(true); tiles.setPickOnBounds(false);
        getChildren().addAll(canvas, tiles);
        for (int i = 0; i < PARTICLES; i++) { px[i] = rnd.nextDouble(); py[i] = rnd.nextDouble(); pvx[i] = (rnd.nextDouble() - 0.5) * 0.012; pvy[i] = -0.004 - rnd.nextDouble() * 0.01; }
        for (int i = 0; i < tile.length; i++) {
            Label l = new Label(TILE_TEXT[i]); l.getStyleClass().add("float-tile"); l.setManaged(false); l.setMouseTransparent(true);
            tile[i] = l; tiles.getChildren().add(l);
            tbx[i] = 0.06 + rnd.nextDouble() * 0.88; tby[i] = 0.08 + rnd.nextDouble() * 0.84;
            tax[i] = 14 + rnd.nextDouble() * 26; tay[i] = 14 + rnd.nextDouble() * 26; tsp[i] = 0.25 + rnd.nextDouble() * 0.5; tph[i] = rnd.nextDouble() * 6.28;
        }
        timer = new AnimationTimer() {
            private long last;
            @Override public void handle(long now) {
                if (last == 0) last = now;
                double dt = Math.min(0.05, (now - last) / 1e9); last = now;
                draw(now / 1e9, dt);
            }
        };
        sceneProperty().addListener((o, a, b) -> { if (b == null) timer.stop(); else timer.start(); });
    }

    /** Lite mode (used behind data screens): no floating tiles and fewer effects. */
    public void setLite(boolean lite) { this.lite = lite; tiles.setVisible(!lite); }
    public boolean isLite() { return lite; }

    @Override protected void layoutChildren() {
        canvas.setWidth(Math.max(1, getWidth())); canvas.setHeight(Math.max(1, getHeight()));
        tiles.resizeRelocate(0, 0, getWidth(), getHeight());
    }

    private void draw(double t, double dt) {
        double w = canvas.getWidth(), h = canvas.getHeight();
        if (w < 20 || h < 20) return;
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setGlobalAlpha(1);
        g.setFill(new LinearGradient(0, 0, 0, h, false, CycleMethod.NO_CYCLE, new Stop(0, Color.web("#040815")), new Stop(1, Color.web("#0a1c42"))));
        g.fillRect(0, 0, w, h);

        // pulsing glows
        double pulse = 0.5 + 0.5 * Math.sin(t * 0.9);
        glow(g, w * 0.22, h * 0.30, Math.max(w, h) * (0.45 + 0.05 * pulse), 0.10 + 0.07 * pulse, "#22d3ee");
        glow(g, w * 0.80, h * 0.78, Math.max(w, h) * 0.42, 0.08 + 0.06 * (1 - pulse), "#3b82f6");

        // drifting grid
        double gap = 48, off = (t * 12) % gap;
        g.setStroke(Color.rgb(34, 211, 238, lite ? 0.04 : 0.07)); g.setLineWidth(1);
        for (double x = -gap + off; x < w; x += gap) g.strokeLine(x, 0, x, h);
        for (double y = -gap + off; y < h; y += gap) g.strokeLine(0, y, w, y);

        // scan band
        double bandH = 110, y = ((t * 70) % (h + bandH * 2)) - bandH;
        g.setFill(new LinearGradient(0, y, 0, y + bandH, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(34, 211, 238, 0)), new Stop(0.85, Color.rgb(34, 211, 238, lite ? 0.05 : 0.10)), new Stop(1, Color.rgb(34, 211, 238, 0.0))));
        g.fillRect(0, y, w, bandH);

        // particle network
        int n = lite ? PARTICLES / 2 : PARTICLES;
        for (int i = 0; i < n; i++) {
            px[i] += pvx[i] * dt * 6; py[i] += pvy[i] * dt * 6;
            if (py[i] < -0.02) { py[i] = 1.02; px[i] = rnd.nextDouble(); }
            if (px[i] < -0.02) px[i] = 1.02; if (px[i] > 1.02) px[i] = -0.02;
        }
        for (int i = 0; i < n; i++) {
            double x1 = px[i] * w, y1 = py[i] * h;
            for (int j = i + 1; j < n; j++) {
                double dx = x1 - px[j] * w, dy = y1 - py[j] * h, d2 = dx * dx + dy * dy;
                if (d2 < 120 * 120) { g.setStroke(Color.rgb(34, 211, 238, 0.22 * (1 - Math.sqrt(d2) / 120))); g.strokeLine(x1, y1, px[j] * w, py[j] * h); }
            }
            double tw = 0.45 + 0.4 * Math.sin(t * 1.6 + i);
            g.setFill(Color.rgb(125, 235, 250, tw)); g.fillOval(x1 - 1.6, y1 - 1.6, 3.2, 3.2);
        }

        if (!lite) {
            for (int i = 0; i < tile.length; i++) {
                Label l = tile[i];
                double x = tbx[i] * w + Math.sin(t * tsp[i] + tph[i]) * tax[i], yy = tby[i] * h + Math.cos(t * tsp[i] * 0.8 + tph[i]) * tay[i];
                l.relocate(x - l.prefWidth(-1) / 2, yy - l.prefHeight(-1) / 2);
                l.setRotate(Math.sin(t * tsp[i] + tph[i]) * 5);
                l.setOpacity(0.55 + 0.35 * Math.sin(t * 0.8 + tph[i]));
            }
        }
    }

    private static void glow(GraphicsContext g, double cx, double cy, double r, double alpha, String color) {
        Color c = Color.web(color);
        g.setFill(new RadialGradient(0, 0, cx, cy, r, false, CycleMethod.NO_CYCLE, new Stop(0, Color.color(c.getRed(), c.getGreen(), c.getBlue(), alpha)), new Stop(1, Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0))));
        g.fillRect(0, 0, g.getCanvas().getWidth(), g.getCanvas().getHeight());
    }
}
