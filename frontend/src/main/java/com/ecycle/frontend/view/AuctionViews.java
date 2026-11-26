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
import java.util.List;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

/** Feature B UI: reverse auction browsing, bidding, bid history and the two leaderboards (highest price vs highest Circular Bid Score). */
public final class AuctionViews {
    private AuctionViews() {}

    private static String left(long secs) { return secs <= 0 ? "ended" : String.format("%dd %02dh %02dm", secs / 86400, (secs % 86400) / 3600, (secs % 3600) / 60); }

    private static Node list(String title, String sub, String path) {
        VBox root = new VBox(12); root.setPadding(new Insets(20));
        FlowPane cards = UI.flow(16);
        Label count = UI.label("", "muted");
        Runnable[] reload = new Runnable[1];
        reload[0] = () -> Async.run(() -> ApiClient.get(path), auctions -> {
            cards.getChildren().clear();
            count.setText(auctions.size() + " auction(s)");
            if (auctions.isEmpty()) cards.getChildren().add(UI.card(UI.label("No auctions to show right now.", "muted")));
            auctions.forEach(auction -> cards.getChildren().add(auctionCard(auction)));
        });
        root.getChildren().addAll(UI.pageHeader(title, sub, UI.secondary("Refresh", () -> reload[0].run())), count, UI.scroll(cards));
        VBox.setVgrow(root.getChildren().get(2), Priority.ALWAYS);
        reload[0].run();
        return root;
    }
    public static Node browse() { return list("Reverse auctions", "Browse material-recovery lots, compare bids and open an auction to place your offer.", "/api/auctions"); }
    public static Node mine() { return list("My auctions", "Follow auctions for your listings and the bids you have placed.", "/api/auctions/mine"); }

    private static Node auctionCard(JsonNode auction) {
        long id = J.l(auction, "id"), listingId = J.l(auction, "listingId");
        VBox card = new VBox(10); card.setPadding(new Insets(12)); card.setPrefWidth(340); card.setMaxWidth(340); card.getStyleClass().add("product-card");
        StackPane photo = new StackPane(); photo.setPrefSize(314, 176); photo.setMinHeight(176); photo.getStyleClass().add("photo-ph");
        photo.getChildren().add(UI.label("Loading listing photo...", "muted"));
        Async.run(() -> ApiClient.get("/api/listings/" + listingId), listing -> {
            photo.getChildren().clear();
            JsonNode images = listing.path("imageIds");
            if (images.isArray() && !images.isEmpty()) {
                photo.getChildren().add(ImageCache.view("/api/listings/" + listingId + "/images/" + images.get(0).asLong(), 314, 176));
            } else {
                photo.getChildren().add(UI.label("No product photo", "muted"));
            }
        }, error -> {
            photo.getChildren().setAll(UI.label("Listing preview unavailable", "muted"));
            Alerts.error(error);
        });

        Label title = UI.label(J.s(auction, "listingName"), "section-title");
        HBox status = UI.row(UI.badge(J.s(auction, "status")), UI.grow(), UI.label("Auction #" + id, "muted"));
        Label time = UI.label("OPEN".equals(J.s(auction, "status")) ? "Closes in " + left(J.l(auction, "secondsRemaining")) : "Ended " + J.date(auction, "endsAt"), "muted");
        Label bidCount = UI.label(J.l(auction, "bidCount") + " bid(s)  ·  Minimum " + J.money(auction, "minPrice"), "label");
        JsonNode highestBid = auction.path("highestPriceBid");
        Label highest = UI.label(highestBid.isNull() ? "No bids yet" : "Highest offer: " + J.money(auction, "highestPriceBid.price"), highestBid.isNull() ? "muted" : "price");
        String score = auction.path("highestScoreBid").isNull() ? "No scored bids yet" :
                String.format("Best Circular Bid Score: %.1f", J.d(auction, "highestScoreBid.circularBidScore"));
        HBox actions = new HBox(8, UI.primary("Open auction", () -> open(id)), UI.secondary("View product", () -> new ProductDialog(listingId).show()));
        card.getChildren().addAll(photo, status, title, time, bidCount, highest, UI.label(score, "muted"), actions);
        card.setOnMouseClicked(e -> { if (e.getTarget() == card || e.getTarget() == title) open(id); });
        return card;
    }

    public static void open(long id) {
        Dialog<ButtonType> d = new Dialog<>(); d.setTitle("Auction #" + id); d.getDialogPane().getStylesheets().add(Nav.css());
        d.getDialogPane().setContent(panel(id)); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.getDialogPane().setPrefSize(980, 660); d.setResizable(true); d.show();
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
        VBox bids = new VBox(8);
        boolean sellerCanAccept = Session.is("SELLER") && ("OPEN".equals(J.s(a, "status")) || "CLOSED_PENDING_SELLER".equals(J.s(a, "status")));
        for (JsonNode bid : a.path("bids")) bids.getChildren().add(bidCard(id, bid, reload, sellerCanAccept));
        if (bids.getChildren().isEmpty()) bids.getChildren().add(UI.card(UI.label("No bids yet. Verified buyers can submit a price and recovery plan while the auction is open.", "muted")));
        HBox actions = new HBox(10);
        if (open && Session.is("BUYER")) actions.getChildren().add(UI.primary("Place a bid", () -> FormDialog.show("Bid on " + J.s(a, "listingName"), "Submit bid", List.of(
                FormDialog.num("price", "Your price (INR)", true, null), FormDialog.combo("pathway", "Intended processing pathway", true, FormDialog.enumOpts(Enums.Pathway.class, false), "RECYCLE"),
                FormDialog.area("recoveryPlan", "Recovery plan (how you will process and recover material)", false)),
                b -> () -> ApiClient.post("/api/auctions/" + id + "/bids", b), r -> { Alerts.info("Bid placed. Your recovery capability and environmental rating come from your verified buyer profile."); reload.run(); })));
        if (sellerCanAccept) {
            if (open && J.l(a, "bidCount") == 0) actions.getChildren().add(UI.danger("Cancel auction", () -> Alerts.confirm("Cancel this auction?", () -> Async.run(() -> ApiClient.post("/api/auctions/" + id + "/cancel", null), r -> reload.run()))));
        }
        actions.getChildren().add(UI.secondary("Refresh", reload));
        return new VBox(12, head, boards, UI.label("Bids and recovery plans", "section-title"), bids, actions);
    }

    private static Node bidCard(long auctionId, JsonNode bid, Runnable reload, boolean sellerCanAccept) {
        VBox card = UI.card();
        HBox top = UI.row(UI.label(J.money(bid, "price"), "price"), UI.grow(), UI.label(String.format("Score %.1f", J.d(bid, "circularBidScore")), "section-title"));
        card.getChildren().addAll(top, UI.label(J.s(bid, "bidderLabel") + "  ·  " + J.date(bid, "createdAt"), "muted"),
                UI.label(J.human(J.s(bid, "pathway")) + "  ·  Recovery " + J.s(bid, "recoveryCapability") + "  ·  Environment " + J.s(bid, "environmentalPerformance"), "label"));
        if (!J.s(bid, "recoveryPlan").isBlank()) card.getChildren().add(UI.label("Recovery plan: " + J.s(bid, "recoveryPlan"), "muted"));
        if (sellerCanAccept) card.getChildren().add(UI.primary("Accept this bid", () -> Alerts.confirm(
                "Accept " + J.money(bid, "price") + " from " + J.s(bid, "bidderLabel") + "? An order will be created for that buyer.",
                () -> Async.run(() -> ApiClient.post("/api/auctions/" + auctionId + "/accept/" + J.l(bid, "id"), null),
                        result -> { Alerts.info("Bid accepted. The buyer was notified to pay."); reload.run(); }))));
        return card;
    }
    private static Node leader(String title, JsonNode bid) {
        VBox v = UI.card(UI.label(title, "stat-title"));
        if (bid == null || bid.isNull() || bid.isMissingNode()) v.getChildren().add(UI.label("No bids yet", "muted"));
        else v.getChildren().addAll(UI.label(J.money(bid, "price"), "stat-value"), UI.label(J.s(bid, "bidderLabel") + "  |  " + J.human(J.s(bid, "pathway")), "muted"),
                UI.label(String.format("Circular Bid Score %.1f  (recovery %s, environment %s)", J.d(bid, "circularBidScore"), J.s(bid, "recoveryCapability"), J.s(bid, "environmentalPerformance")), "muted"));
        return v;
    }
}
