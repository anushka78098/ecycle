package com.ecycle.frontend.component;

import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;

/** Generic vertical timeline (title / meta / note rows) used by pickup tracking and the chain of custody. */
public final class EventTimeline extends VBox {
    public record Row(String title, String meta, String note) {}
    public EventTimeline(List<Row> rows) {
        setSpacing(0); setPadding(new Insets(6));
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i); boolean last = i == rows.size() - 1;
            Circle dot = new Circle(7); dot.getStyleClass().add(last ? "tl-dot-current" : "tl-dot");
            Region line = new Region(); line.getStyleClass().add("tl-line"); line.setPrefWidth(2); line.setMinHeight(30); line.setVisible(!last);
            VBox rail = new VBox(dot, line); rail.setAlignment(Pos.TOP_CENTER); rail.setMinWidth(24);
            VBox text = new VBox(2, UI.badge(r.title()), UI.label(r.meta() == null ? "" : r.meta(), "muted"), UI.label(r.note() == null ? "" : r.note(), "tl-note"));
            HBox row = new HBox(12, rail, text); row.setPadding(new Insets(0, 0, 8, 0)); getChildren().add(row);
        }
        if (rows.isEmpty()) getChildren().add(UI.label("No events yet.", "muted"));
    }
}
