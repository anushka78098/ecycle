package com.ecycle.frontend.component;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/** A mock "terminal window" that types out a marketplace listing flow, then loops. Purely decorative. */
public class CodePanel extends VBox {
    private static final String SCRIPT = String.join("\n",
            "POST /api/listings",
            "{ \"item\": \"Dell XPS 13\",",
            "  \"condition\": \"GOOD\",",
            "  \"weightKg\": 1.2 }",
            "",
            "> circularValueScore ... 87",
            "> co2Saved ............ 14.6 kg",
            "> passport ............ minted",
            "> wipe certificate .... signed",
            "> status .............. LISTED");
    private final Label text = new Label();
    private final Timeline typing;
    private int idx;

    public CodePanel() {
        getStyleClass().add("code-window"); setSpacing(0); setMaxWidth(430);
        HBox bar = new HBox(7, dot("#f87171"), dot("#fbbf24"), dot("#34d399")); bar.getStyleClass().add("code-bar");
        Label title = new Label("ecycle-listing.json"); title.getStyleClass().add("code-title");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS); bar.getChildren().addAll(sp, title);
        text.getStyleClass().add("code-text"); text.setMinHeight(200); text.setPrefHeight(200); text.setWrapText(false);
        VBox body = new VBox(text); body.setPadding(new Insets(14, 16, 14, 16));
        getChildren().addAll(bar, body);

        typing = new Timeline(new KeyFrame(Duration.millis(42), e -> tick()));
        typing.setCycleCount(Animation.INDEFINITE);
        sceneProperty().addListener((o, a, b) -> { if (b == null) typing.pause(); else typing.play(); });
        Motion.bob(this, 5, 3.2);
        Motion.pulseGlow(this, Color.web("#22d3ee"));
    }

    private void tick() {
        idx++;
        int n = SCRIPT.length(), hold = 55;       // pause ~2.3 s on the finished text before restarting
        if (idx > n + hold) idx = 0;
        boolean caret = (idx / 6) % 2 == 0;
        text.setText(SCRIPT.substring(0, Math.min(idx, n)) + (caret ? "█" : " "));
    }

    private static Circle dot(String c) { Circle d = new Circle(5, Color.web(c)); return d; }
}
