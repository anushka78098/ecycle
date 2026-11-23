package com.ecycle.frontend.component;

import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Visual product genealogy: the product at the root and every dismantled component beneath it, each with stage and current owner. */
public final class GenealogyTree extends TreeView<JsonNode> {
    public GenealogyTree(JsonNode root, java.util.function.Consumer<JsonNode> onSelect) {
        setRoot(build(root)); setShowRoot(true); setPrefHeight(300);
        setCellFactory(tv -> new TreeCell<>() {
            @Override protected void updateItem(JsonNode n, boolean empty) {
                super.updateItem(n, empty);
                if (empty || n == null) { setGraphic(null); setText(null); return; }
                boolean isRoot = n.path("parentCode").isNull() || n.path("parentCode").isMissingNode();
                VBox v = new VBox(2, new HBox(8, UI.label((isRoot ? "[Product] " : "[Component] ") + J.s(n, "name"), "tree-name"), UI.badge(J.s(n, "stage"))),
                        UI.label(J.s(n, "code") + "  |  " + J.s(n, "componentType") + "  |  owner: " + J.s(n, "ownerLabel"), "muted"));
                setGraphic(v); setText(null);
            }
        });
        getSelectionModel().selectedItemProperty().addListener((o, a, b) -> { if (b != null && onSelect != null) onSelect.accept(b.getValue()); });
        getSelectionModel().selectFirst();
    }
    private static TreeItem<JsonNode> build(JsonNode n) {
        TreeItem<JsonNode> t = new TreeItem<>(n); t.setExpanded(true);
        n.path("children").forEach(c -> t.getChildren().add(build(c))); return t;
    }
}
