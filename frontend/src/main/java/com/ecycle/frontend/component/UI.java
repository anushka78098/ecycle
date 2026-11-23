package com.ecycle.frontend.component;

import com.ecycle.frontend.util.J;
import java.util.Set;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;

/** Small builders so every screen shares one visual language. */
public final class UI {
    private UI() {}
    private static final Set<String> GREEN = Set.of("APPROVED", "VERIFIED", "PAID", "DELIVERED", "RESOLVED", "ACTIVE", "PROCESSED", "ACCEPTED", "AWARDED", "LOW", "FULLY_WORKING", "LIKE_NEW", "GOOD", "REUSED", "REPAIRED", "REFURBISHED", "RECYCLED", "TRUE", "YES");
    private static final Set<String> AMBER = Set.of("PENDING", "PENDING_APPROVAL", "PENDING_PAYMENT", "SUBMITTED", "ASSIGNED", "AWAITING_EVIDENCE", "UNDER_REVIEW", "MODERATE", "MEDIUM", "RESERVED", "SHIPPED", "OPEN", "PARTIALLY_REFUNDED", "FAIR", "PARTIALLY_WORKING", "CLOSED_PENDING_SELLER", "HAZARD_HOLD");
    private static final Set<String> RED = Set.of("REJECTED", "SUSPENDED", "CANCELLED", "FAILED", "HIGH", "CRITICAL", "DISPUTED", "SCRAP", "POOR", "NOT_WORKING", "REFUNDED", "WITHDRAWN", "FALSE", "NO");

    public static String badgeClass(String v) {
        String k = v == null ? "" : v.toUpperCase();
        return GREEN.contains(k) ? "badge-green" : AMBER.contains(k) ? "badge-amber" : RED.contains(k) ? "badge-red" : "badge-grey";
    }
    public static Label badge(String raw) {
        Label l = new Label(J.human(raw)); l.getStyleClass().addAll("badge", badgeClass(raw)); return l;
    }
    public static Label label(String text, String... styles) { Label l = new Label(text); l.getStyleClass().addAll(styles); l.setWrapText(true); return l; }
    public static Button button(String text, String style, Runnable action) {
        Button b = new Button(text); b.getStyleClass().add(style); b.setOnAction(e -> action.run()); return b;
    }
    public static Button primary(String text, Runnable a) { return button(text, "btn-primary", a); }
    public static Button secondary(String text, Runnable a) { return button(text, "btn-secondary", a); }
    public static Button danger(String text, Runnable a) { return button(text, "btn-danger", a); }

    public static VBox card(Node... children) { VBox v = new VBox(10, children); v.getStyleClass().add("card"); v.setPadding(new Insets(16)); return v; }
    public static VBox statCard(String title, String value, String hint) {
        VBox v = new VBox(4, label(title, "stat-title"), label(value, "stat-value"), label(hint == null ? "" : hint, "muted"));
        v.getStyleClass().add("stat-card"); v.setPadding(new Insets(16)); v.setMinWidth(190); return v;
    }
    public static VBox pageHeader(String title, String subtitle, Node... actions) {
        HBox row = new HBox(10, new VBox(2, label(title, "page-title"), label(subtitle == null ? "" : subtitle, "page-sub")));
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS); row.getChildren().add(sp); row.getChildren().addAll(actions); row.setAlignment(Pos.CENTER_LEFT);
        return new VBox(row);
    }
    public static ScrollPane scroll(Node n) { ScrollPane s = new ScrollPane(n); s.setFitToWidth(true); s.getStyleClass().add("clear-scroll"); return s; }
    public static HBox row(Node... n) { HBox h = new HBox(10, n); h.setAlignment(Pos.CENTER_LEFT); return h; }
    public static Region grow() { Region r = new Region(); HBox.setHgrow(r, Priority.ALWAYS); return r; }
    public static FlowPane flow(double gap) { FlowPane f = new FlowPane(gap, gap); f.setPadding(new Insets(4)); return f; }
}
