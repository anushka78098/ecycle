package com.ecycle.frontend.component;

import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;

/** Vertical lifecycle timeline: current stage highlighted, earlier stages muted, each with time, actor and pathway. */
public final class Timeline extends VBox {
    public Timeline(JsonNode events) {
        setSpacing(0); setPadding(new Insets(6));
        int n = events.size();
        for (int i = 0; i < n; i++) {
            JsonNode e = events.get(i); boolean last = i == n - 1;
            Circle dot = new Circle(7); dot.getStyleClass().add(last ? "tl-dot-current" : "tl-dot");
            Region line = new Region(); line.getStyleClass().add("tl-line"); line.setPrefWidth(2); line.setMinHeight(34); line.setVisible(!last);
            VBox rail = new VBox(dot, line); rail.setAlignment(Pos.TOP_CENTER); rail.setMinWidth(24);
            String path = J.s(e, "pathway").isBlank() ? "" : "  |  Pathway: " + J.human(J.s(e, "pathway"));
            VBox text = new VBox(2, UI.badge(J.s(e, "stage")), UI.label(J.date(e, "createdAt") + "  |  " + J.s(e, "actorLabel") + path, "muted"),
                    UI.label(J.s(e, "note"), "tl-note"));
            HBox row = new HBox(12, rail, text); row.setPadding(new Insets(0, 0, 8, 0)); getChildren().add(row);
        }
        if (n == 0) getChildren().add(UI.label("No lifecycle events recorded.", "muted"));
    }
}
