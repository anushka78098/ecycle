package com.ecycle.frontend.component;

import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Declarative data screen: title + toolbar + table backed by a REST call. Rows are JsonNodes; actions enable according to the selected row. */
public class ResourceView extends VBox {
    public record Col(String header, Function<JsonNode, String> value, boolean badge, double width) {}
    private record Act(Button button, Predicate<JsonNode> enabled) {}

    private final TableView<JsonNode> table = new TableView<>();
    private final HBox toolbar = new HBox(8);
    private final Callable<JsonNode> loader;
    private final List<Act> acts = new ArrayList<>();
    private final Label count = UI.label("", "muted");
    private Consumer<JsonNode> onOpen;

    public ResourceView(String title, String subtitle, Callable<JsonNode> loader) {
        this.loader = loader;
        setSpacing(12); setPadding(new Insets(20));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(UI.label("Nothing here yet.", "muted"));
        VBox.setVgrow(table, Priority.ALWAYS);
        Button refresh = UI.secondary("Refresh", this::reload);
        toolbar.getChildren().addAll(refresh, UI.grow(), count); toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        getChildren().addAll(UI.pageHeader(title, subtitle), toolbar, table);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> updateEnabled());
        table.setRowFactory(tv -> { TableRow<JsonNode> r = new TableRow<>(); r.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !r.isEmpty() && onOpen != null) onOpen.accept(r.getItem()); }); return r; });
        reload();
    }

    public ResourceView col(String header, String path) { return col(header, n -> J.s(n, path), false, 0); }
    public ResourceView col(String header, Function<JsonNode, String> f) { return col(header, f, false, 0); }
    public ResourceView col(String header, Function<JsonNode, String> f, boolean badge, double width) {
        TableColumn<JsonNode, JsonNode> c = new TableColumn<>(header);
        c.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        c.setCellFactory(x -> new TableCell<>() {
            @Override protected void updateItem(JsonNode n, boolean empty) {
                super.updateItem(n, empty);
                if (empty || n == null) { setText(null); setGraphic(null); return; }
                String v = f.apply(n);
                if (badge && !v.isBlank()) { setText(null); setGraphic(UI.badge(v)); } else { setGraphic(null); setText(badge ? J.human(v) : v); }
            }
        });
        if (width > 0) c.setPrefWidth(width);
        table.getColumns().add(c); return this;
    }
    public ResourceView nodeColumn(String header, Function<JsonNode, Node> content, double width) {
        TableColumn<JsonNode, JsonNode> c = new TableColumn<>(header);
        c.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        c.setCellFactory(x -> new TableCell<>() {
            @Override protected void updateItem(JsonNode n, boolean empty) {
                super.updateItem(n, empty);
                setText(null);
                setGraphic(empty || n == null ? null : content.apply(n));
            }
        });
        if (width > 0) c.setPrefWidth(width);
        table.getColumns().add(c); return this;
    }
    public ResourceView badge(String header, String path) { return col(header, n -> J.s(n, path), true, 0); }
    public ResourceView money(String header, String path) { return col(header, n -> J.money(n, path)); }
    public ResourceView date(String header, String path) { return col(header, n -> J.date(n, path)); }
    public ResourceView yesNo(String header, String path) { return col(header, n -> J.b(n, path) ? "Yes" : "No"); }

    public ResourceView action(String label, Predicate<JsonNode> enabled, Consumer<JsonNode> run) { return action(label, "btn-secondary", enabled, run); }
    public ResourceView action(String label, String style, Predicate<JsonNode> enabled, Consumer<JsonNode> run) {
        Button b = UI.button(label, style, () -> { JsonNode sel = table.getSelectionModel().getSelectedItem(); if (sel != null) run.accept(sel); });
        b.setDisable(true); acts.add(new Act(b, enabled)); toolbar.getChildren().add(toolbar.getChildren().size() - 2, b); return this;
    }
    public ResourceView tool(String label, Runnable run) { toolbar.getChildren().add(toolbar.getChildren().size() - 2, UI.primary(label, run)); return this; }
    public ResourceView onOpen(Consumer<JsonNode> c) { this.onOpen = c; return this; }
    public ResourceView node(Node n) { toolbar.getChildren().add(toolbar.getChildren().size() - 2, n); return this; }
    public JsonNode selected() { return table.getSelectionModel().getSelectedItem(); }

    private void updateEnabled() {
        JsonNode sel = table.getSelectionModel().getSelectedItem();
        for (Act a : acts) a.button().setDisable(sel == null || !a.enabled().test(sel));
    }
    public void reload() {
        Async.run(loader, data -> {
            table.setItems(FXCollections.observableArrayList(iterable(data)));
            count.setText(table.getItems().size() + " item(s)"); updateEnabled();
        });
    }
    private static List<JsonNode> iterable(JsonNode arr) { List<JsonNode> l = new ArrayList<>(); if (arr != null && arr.isArray()) arr.forEach(l::add); return l; }
}
