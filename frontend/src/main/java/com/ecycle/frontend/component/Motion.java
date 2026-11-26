package com.ecycle.frontend.component;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/** Small reusable animations: entrance (fade + slide) and a pulsing glow that stops itself when the node leaves the screen. */
public final class Motion {
    private Motion() {}

    public static void enter(Node n) { enter(n, 0); }

    /** Fades the node in while sliding it up a few pixels. */
    public static void enter(Node n, double delayMs) {
        n.setOpacity(0); n.setTranslateY(14);
        FadeTransition f = new FadeTransition(Duration.millis(380), n); f.setFromValue(0); f.setToValue(1); f.setInterpolator(Interpolator.EASE_OUT);
        TranslateTransition t = new TranslateTransition(Duration.millis(380), n); t.setFromY(14); t.setToY(0); t.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition p = new ParallelTransition(f, t); p.setDelay(Duration.millis(delayMs)); p.play();
    }

    /** Breathing glow around a node. Stops automatically when the node is removed from its scene. */
    public static javafx.animation.Timeline pulseGlow(Node n, Color color) {
        DropShadow ds = new DropShadow(10, color); ds.setSpread(0.08); n.setEffect(ds);
        javafx.animation.Timeline tl = new javafx.animation.Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(ds.radiusProperty(), 8)),
                new KeyFrame(Duration.seconds(1.5), new KeyValue(ds.radiusProperty(), 26, Interpolator.EASE_BOTH)));
        tl.setAutoReverse(true); tl.setCycleCount(Animation.INDEFINITE);
        n.sceneProperty().addListener((o, a, b) -> { if (b == null) tl.pause(); else tl.play(); });
        tl.play();
        return tl;
    }

    /** Gentle up-and-down floating motion. */
    public static void bob(Node n, double amplitude, double seconds) {
        TranslateTransition t = new TranslateTransition(Duration.seconds(seconds), n);
        t.setFromY(-amplitude); t.setToY(amplitude); t.setAutoReverse(true); t.setCycleCount(Animation.INDEFINITE); t.setInterpolator(Interpolator.EASE_BOTH);
        n.sceneProperty().addListener((o, a, b) -> { if (b == null) t.pause(); else t.play(); });
        t.play();
    }
}
