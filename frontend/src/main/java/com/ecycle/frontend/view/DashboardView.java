package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.component.Charts;
import com.ecycle.frontend.component.UI;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Live dashboard for any role. Every number comes from /api/dashboard/{role}; nothing is hard-coded. */
public class DashboardView extends VBox {
    private final String role;
    public DashboardView(String role) {
        this.role = role; setSpacing(16); setPadding(new Insets(20));
        getChildren().add(UI.label("Loading dashboard...", "muted")); load();
    }
    private void load() { Async.run(() -> ApiClient.get("/api/dashboard/" + role), this::render); }

    private static String title(String key) {
        String s = key.replaceAll("([A-Z])", " $1").toLowerCase(); return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
    private static String fmt(String key, JsonNode v) {
        String k = key.toLowerCase();
        if (k.contains("earnings") || k.contains("spent") || k.contains("merchandise")) return J.money(v.asDouble());
        if (k.endsWith("kg")) return String.format("%,.1f kg", v.asDouble());
        if (k.endsWith("hours")) return String.format("%.1f h", v.asDouble());
        return v.isIntegralNumber() ? String.format("%,d", v.asLong()) : v.asText();
    }
    private void render(JsonNode d) {
        VBox body = new VBox(16); body.setPadding(new Insets(4));
        body.getChildren().add(UI.pageHeader(J.human(role) + " dashboard", "Live figures from the platform database", UI.secondary("Refresh", () -> { getChildren().setAll(UI.label("Refreshing...", "muted")); load(); })));
        FlowPane stats = UI.flow(14);
        for (Iterator<Map.Entry<String, JsonNode>> it = d.path("stats").fields(); it.hasNext(); ) { var e = it.next(); stats.getChildren().add(UI.statCard(title(e.getKey()), fmt(e.getKey(), e.getValue()), null)); }
        body.getChildren().add(stats);
        FlowPane charts = UI.flow(14);
        d.path("charts").fields().forEachRemaining(e -> { VBox c = (VBox) Charts.auto(e.getKey(), e.getValue()); c.setPrefWidth(540); charts.getChildren().add(c); });
        body.getChildren().add(charts);
        JsonNode recent = d.path("recent");
        if (recent.size() > 0) {
            TableView<JsonNode> t = new TableView<>(); t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); t.setPrefHeight(60 + recent.size() * 32);
            recent.get(0).fieldNames().forEachRemaining(f -> {
                TableColumn<JsonNode, String> c = new TableColumn<>(title(f));
                c.setCellValueFactory(cd -> { JsonNode v = cd.getValue().path(f); String k = f.toLowerCase();
                    String s = k.contains("date") ? J.date(v.asText()) : k.contains("amount") ? J.money(v.asDouble()) : (k.equals("status") || k.equals("priority") || k.equals("category")) ? J.human(v.asText()) : v.asText();
                    return new javafx.beans.property.SimpleStringProperty(s); });
                t.getColumns().add(c);
            });
            List<JsonNode> rows = new ArrayList<>(); recent.forEach(rows::add);
            t.setItems(FXCollections.observableArrayList(rows));
            body.getChildren().addAll(UI.label("Recent activity", "section-title"), t);
        }
        getChildren().setAll(UI.scroll(body));
        VBox.setVgrow(getChildren().get(0), javafx.scene.layout.Priority.ALWAYS);
    }
}
