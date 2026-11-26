package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.api.Session;
import com.ecycle.frontend.component.*;
import com.ecycle.frontend.navigation.Nav;
import com.ecycle.frontend.util.Alerts;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.ecycle.shared.Enums;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

/** Feature B UI: reverse auction browsing, bidding, bid history and the two leaderboards (highest price vs highest Circular Bid Score). */
public final class AuctionViews {
    private AuctionViews() {}

    private static String left(long secs) { return secs <= 0 ? "ended" : String.format("%dd %02dh %02dm", secs / 86400, (secs % 86400) / 3600, (secs % 3600) / 60); }

    private static ResourceView list(String title, String sub, String path) {
        ResourceView v = new ResourceView(title, sub, () -> ApiClient.get(path));
        v.col("Auction", "id").col("Product", "listingName").badge("Status", "status").col("Ends", n -> J.date(n, "endsAt")).col("Time left", n -> left(J.l(n, "secondsRemaining")))
                .col("Bids", "bidCount").money("Minimum", "minPrice").money("Highest offer", "highestPriceBid.price").col("Best score", n -> n.path("highestScoreBid").isNull() ? "" : String.format("%.1f", J.d(n, "highestScoreBid.circularBidScore")))
                .action("Open auction", n -> true, n -> open(J.l(n, "id"))).onOpen(n -> open(J.l(n, "id")));
        return v;
    }
    public static ResourceView browse() { return list("Reverse auctions", "Open material-recovery auctions. Only admin-verified buyers can bid.", "/api/auctions"); }
    public static ResourceView mine() { return list("My auctions", "Auctions on your listings, or that you bid in", "/api/auctions/mine"); }

    public static void open(long id) {
        Dialog<ButtonType> d = new Dialog<>(); d.setTitle("Auction #" + id); d.getDialogPane().getStylesheets().add(Nav.css());
        d.getDialogPane().setContent(panel(id)); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.getDialogPane().setPrefSize(980, 660); d.setResizable(true); d.show();
        d.setOnHidden(e -> { }); 
    }

    public static Node panel(long auctionId) {
        VBox root = new VBox(12); root.setPadding(new Insets(10));
        Runnable[] reload = new Runnable[1];
        reload[0] = () -> Async.run(() -> ApiClient.get("/api/auctions/" + auctionId), a -> { root.getChildren().setAll(build(a, reload[0])); });
        reload[0].run();
        return UI.scroll(root);
    }

    private static Node build(JsonNode a, Runnable reload) {
        long id = J.l(a, "id"); boolean open = "OPEN".equals(J.s(a, "status"));
        Label countdown = UI.label(open ? "Closes in " + left(J.l(a, "secondsRemaining")) : "", "section-title");
        long[] secs = {J.l(a, "secondsRemaining")};
        if (open) {
            javafx.animation.Timeline t = new javafx.animation.Timeline(new KeyFrame(Duration.seconds(1), e -> { secs[0]--; countdown.setText(secs[0] > 0 ? "Closes in " + left(secs[0]) : "Closing..."); }));
            t.setCycleCount(Animation.INDEFINITE); t.play(); countdown.sceneProperty().addListener((o, x, s) -> { if (s == null) t.stop(); });
        }
        VBox head = UI.card(UI.row(UI.label(J.s(a, "listingName"), "page-title"), UI.badge(J.s(a, "status")), UI.grow(), countdown),
                UI.label("Reference price (best known): " + J.money(a, "referencePrice") + "   |   Minimum bid: " + J.money(a, "minPrice") + "   |   Bids: " + J.l(a, "bidCount"), "muted"));
        HBox boards = new HBox(14, leader("Highest financial offer", a.path("highestPriceBid")), leader("Highest Circular Bid Score", a.path("highestScoreBid")));
        HBox.setHgrow(boards.getChildren().get(0), Priority.ALWAYS); HBox.setHgrow(boards.getChildren().get(1), Priority.ALWAYS);
        TableView<JsonNode> t = new TableView<>(); t.setPrefHeight(260); t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addCol(t, "Time", n -> J.date(n, "createdAt")); addCol(t, "Bidder", n -> J.s(n, "bidderLabel")); addCol(t, "Price", n -> J.money(n, "price")); addCol(t, "Pathway", n -> J.human(J.s(n, "pathway")));
        addCol(t, "Recovery", n -> J.s(n, "recoveryCapability")); addCol(t, "Env. perf.", n -> J.s(n, "environmentalPerformance")); addCol(t, "Circular Bid Score", n -> String.format("%.1f", J.d(n, "circularBidScore")));
        addCol(t, "Recovery plan", n -> J.s(n, "recoveryPlan"));
        List<JsonNode> rows = new ArrayList<>(); a.path("bids").forEach(rows::add); t.setItems(FXCollections.observableArrayList(rows));
        HBox actions = new HBox(10);
        if (open && Session.is("BUYER")) actions.getChildren().add(UI.primary("Place a bid", () -> FormDialog.show("Bid on " + J.s(a, "listingName"), "Submit bid", List.of(
                FormDialog.num("price", "Your price (INR)", true, null), FormDialog.combo("pathway", "Intended processing pathway", true, FormDialog.enumOpts(Enums.Pathway.class, false), "RECYCLE"),
                FormDialog.area("recoveryPlan", "Recovery plan (how you will process and recover material)", false)),
                b -> () -> ApiClient.post("/api/auctions/" + id + "/bids", b), r -> { Alerts.info("Bid placed. Your recovery capability and environmental rating come from your verified buyer profile."); reload.run(); })));
        if (Session.is("SELLER") && ("OPEN".equals(J.s(a, "status")) || "CLOSED_PENDING_SELLER".equals(J.s(a, "status")))) {
            actions.getChildren().add(UI.primary("Accept selected bid", () -> { JsonNode s = t.getSelectionModel().getSelectedItem(); if (s == null) { Alerts.info("Select a bid in the table first."); return; }
                Alerts.confirm("Accept the bid of " + J.money(s, "price") + " from " + J.s(s, "bidderLabel") + "? An order will be created for that buyer.",
                        () -> Async.run(() -> ApiClient.post("/api/auctions/" + id + "/accept/" + J.l(s, "id"), null), r -> { Alerts.info("Bid accepted. The buyer was notified to pay."); reload.run(); })); }));
            if (open && J.l(a, "bidCount") == 0) actions.getChildren().add(UI.danger("Cancel auction", () -> Alerts.confirm("Cancel this auction?", () -> Async.run(() -> ApiClient.post("/api/auctions/" + id + "/cancel", null), r -> reload.run()))));
        }
        actions.getChildren().add(UI.secondary("Refresh", reload));
        return new VBox(12, head, boards, UI.label("Bidding history (append-only, auditable)", "section-title"), t, actions);
    }

    private static void addCol(TableView<JsonNode> t, String h, java.util.function.Function<JsonNode, String> f) {
        TableColumn<JsonNode, String> c = new TableColumn<>(h); c.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(f.apply(cd.getValue()))); t.getColumns().add(c);
    }
    private static Node leader(String title, JsonNode bid) {
        VBox v = UI.card(UI.label(title, "stat-title"));
        if (bid == null || bid.isNull() || bid.isMissingNode()) v.getChildren().add(UI.label("No bids yet", "muted"));
        else v.getChildren().addAll(UI.label(J.money(bid, "price"), "stat-value"), UI.label(J.s(bid, "bidderLabel") + "  |  " + J.human(J.s(bid, "pathway")), "muted"),
                UI.label(String.format("Circular Bid Score %.1f  (recovery %s, environment %s)", J.d(bid, "circularBidScore"), J.s(bid, "recoveryCapability"), J.s(bid, "environmentalPerformance")), "muted"));
        return v;
    }
}
