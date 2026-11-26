package com.ecycle.frontend.component;

import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.scene.chart.*;
import javafx.scene.layout.VBox;

/** JavaFX chart builders over {label: number} JSON objects. */
public final class Charts {
    private Charts() {}
    public static PieChart pie(String title, JsonNode data) {
        PieChart c = new PieChart(); c.setTitle(title); c.setLegendVisible(true); c.setLabelsVisible(false); c.setPrefHeight(280);
        data.fields().forEachRemaining(e -> c.getData().add(new PieChart.Data(J.human(e.getKey()) + " (" + e.getValue().asLong() + ")", e.getValue().asDouble())));
        return c;
    }
    public static BarChart<String, Number> bar(String title, JsonNode data) {
        CategoryAxis x = new CategoryAxis(); NumberAxis y = new NumberAxis(); BarChart<String, Number> c = new BarChart<>(x, y); c.setTitle(title); c.setLegendVisible(false); c.setPrefHeight(280);
        XYChart.Series<String, Number> s = new XYChart.Series<>(); data.fields().forEachRemaining(e -> s.getData().add(new XYChart.Data<>(J.human(e.getKey()), e.getValue().asDouble())));
        c.getData().add(s); return c;
    }
    public static LineChart<String, Number> line(String title, JsonNode data) {
        CategoryAxis x = new CategoryAxis(); NumberAxis y = new NumberAxis(); LineChart<String, Number> c = new LineChart<>(x, y); c.setTitle(title); c.setLegendVisible(false); c.setPrefHeight(280);
        XYChart.Series<String, Number> s = new XYChart.Series<>(); data.fields().forEachRemaining(e -> s.getData().add(new XYChart.Data<>(e.getKey(), e.getValue().asDouble())));
        c.getData().add(s); return c;
    }
    /** Picks a sensible chart: monthly series as lines, small category splits as pies, everything else as bars. */
    public static javafx.scene.Node auto(String title, JsonNode data) {
        VBox box = UI.card();
        if (data.size() == 0) { box.getChildren().addAll(UI.label(title, "section-title"), UI.label("No data yet.", "muted")); return box; }
        String t = title.toLowerCase();
        javafx.scene.Node chart = t.contains("month") ? line(title, data) : (data.size() <= 8 && !t.contains("category")) ? pie(title, data) : bar(title, data);
        box.getChildren().add(chart); return box;
    }
}
