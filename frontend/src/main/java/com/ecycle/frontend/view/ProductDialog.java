package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.api.Session;
import com.ecycle.frontend.component.*;
import com.ecycle.frontend.navigation.Nav;
import com.ecycle.frontend.util.Alerts;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Product detail window: overview and actions, Circular Value Score, lifecycle timeline, digital passport, safety and auction. */
public class ProductDialog {
    private final long id;
    public ProductDialog(long id) { this.id = id; }
    public void show() { Async.run(() -> ApiClient.get("/api/listings/" + id), this::build); }

    private void build(JsonNode l) {
        TabPane tabs = new TabPane(); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(new Tab("Overview", overview(l)));
        tabs.getTabs().add(new Tab("Circular Value Score", score(l)));
        Tab life = lazy("Lifecycle", () -> ApiClient.get("/api/listings/" + id + "/lifecycle"), n -> UI.scroll(new Timeline(n))); tabs.getTabs().add(life);
        Tab pass = lazy("Digital passport", () -> ApiClient.get("/api/listings/" + id + "/passport"), n -> PassportViews.panel(n, () -> { })); tabs.getTabs().add(pass);
        tabs.getTabs().add(new Tab("Safety & data", safety(l)));
        if (J.b(l, "auctionEnabled") && J.l(l, "auctionId") > 0) { Tab a = new Tab("Reverse auction", AuctionViews.panel(J.l(l, "auctionId"))); tabs.getTabs().add(a); }
        Dialog<ButtonType> d = new Dialog<>(); d.setTitle(J.s(l, "name")); d.getDialogPane().getStylesheets().add(Nav.css());
        d.getDialogPane().setContent(tabs); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.getDialogPane().setPrefSize(940, 680); d.setResizable(true); d.show();
        pass.setUserData(d);
    }

    private static Tab lazy(String title, java.util.concurrent.Callable<JsonNode> load, java.util.function.Function<JsonNode, Node> render) {
        Tab t = new Tab(title); boolean[] done = {false}; t.setContent(UI.label("Loading...", "muted"));
        t.setOnSelectionChanged(e -> { if (t.isSelected() && !done[0]) { done[0] = true;
            Async.run(load, n -> t.setContent(render.apply(n)), err -> t.setContent(UI.label(err.getMessage(), "error-text"))); } });
        return t;
    }

    private Node overview(JsonNode l) {
        VBox photos = new VBox(8); photos.setPrefWidth(340);
        StackPane main = new StackPane(); main.setPrefSize(330, 240); main.getStyleClass().add("photo-ph");
        JsonNode ids = l.path("imageIds");
        if (ids.size() == 0) main.getChildren().add(UI.label("No photos uploaded", "muted"));
        else { main.getChildren().add(ImageCache.view(img(ids.get(0).asLong()), 330, 240));
            HBox thumbs = new HBox(6); for (JsonNode i : ids) { var t = ImageCache.view(img(i.asLong()), 70, 52); t.setOnMouseClicked(e -> main.getChildren().setAll(ImageCache.view(img(i.asLong()), 330, 240))); thumbs.getChildren().add(t); }
            photos.getChildren().add(thumbs); }
        photos.getChildren().add(0, main);

        GridPane g = new GridPane(); g.setHgap(14); g.setVgap(6); int r = 0;
        String[][] rows = {{"Seller", J.s(l, "sellerName") + (J.b(l, "sellerVerified") ? "  (verified)" : "")}, {"Category", J.s(l, "categoryName")}, {"Brand / model", (J.s(l, "brand") + " " + J.s(l, "model")).trim()},
                {"Age", J.s(l, "ageMonths") + " months"}, {"Lot size", J.s(l, "quantity") + " unit(s), " + J.s(l, "weightKg") + " kg each"}, {"Pickup", (J.s(l, "pickupCity") + " " + J.s(l, "pickupAddress")).trim()}};
        for (String[] row : rows) { g.add(UI.label(row[0], "muted"), 0, r); g.add(UI.label(row[1], "label"), 1, r++); }
        HBox badges = new HBox(6, UI.badge(J.s(l, "physicalCondition")), UI.badge(J.s(l, "functionalStatus")), UI.badge(J.s(l, "pathway")), UI.badge(J.s(l, "status")));
        VBox right = new VBox(10, UI.label(J.s(l, "name"), "page-title"), UI.label(J.money(l, "expectedPrice") + (J.b(l, "auctionEnabled") ? "  (reference price, reverse auction)" : ""), "price"), badges, g,
                UI.label(J.s(l, "description"), "label"));
        if (J.b(l, "shippingBlocked")) right.getChildren().add(UI.label("HAZARDOUS: normal shipping is blocked. Only verified specialist recyclers can buy this item (" + J.s(l, "specialistRequired") + ").", "error-text"));
        if (J.b(l, "requiresSanitization") && !J.b(l, "sanitizationVerified")) right.getChildren().add(UI.label("Contains storage: data sanitization must be verified before resale or reuse.", "error-text"));
        HBox actions = new HBox(8); boolean live = "APPROVED".equals(J.s(l, "status"));
        if (Session.is("BUYER") && live) {
            if (!J.b(l, "auctionEnabled")) { actions.getChildren().add(UI.primary("Buy now", () -> buy(l))); actions.getChildren().add(UI.secondary("Make an offer", () -> offer(l))); }
            actions.getChildren().add(UI.secondary("Save to wishlist", () -> Async.run(() -> ApiClient.post("/api/wishlist/" + id, null), r2 -> Alerts.info("Saved to your wishlist."))));
            actions.getChildren().add(UI.secondary("Message seller", () -> FormDialog.show("Message the seller", "Send", List.of(FormDialog.area("body", "Message", true)),
                    b -> () -> { b.put("listingId", id); b.put("recipientId", J.l(l, "sellerId")); return ApiClient.post("/api/chat", b); }, r2 -> Alerts.info("Message sent."))));
        }
        right.getChildren().add(actions);
        HBox body = new HBox(18, photos, right); HBox.setHgrow(right, Priority.ALWAYS); body.setPadding(new Insets(14));
        return UI.scroll(body);
    }
    private String img(long imageId) { return "/api/listings/" + id + "/images/" + imageId; }

    private void buy(JsonNode l) {
        FormDialog.show("Buy " + J.s(l, "name"), "Place order", List.of(FormDialog.area("shippingAddress", "Delivery address (or pickup note)", true)),
                b -> () -> { b.put("listingId", id); return ApiClient.post("/api/orders", b); },
                o -> Alerts.confirm("Order #" + o.path("id").asLong() + " created and the product is reserved for you.\nPay " + J.money(o, "amount") + " now?", () -> OrderViews.pay(o, () -> { })));
    }
    private void offer(JsonNode l) {
        FormDialog.show("Make an offer", "Send offer", List.of(FormDialog.num("amount", "Your offer (asking " + J.money(l, "expectedPrice") + ")", true, null), FormDialog.area("message", "Message to seller", false)),
                b -> () -> { b.put("listingId", id); return ApiClient.post("/api/offers", b); }, r -> Alerts.info("Offer sent. You will be notified when the seller responds."));
    }

    private Node score(JsonNode l) {
        JsonNode s = l.path("score"); VBox v = new VBox(12); v.setPadding(new Insets(16));
        v.getChildren().addAll(UI.label(String.format("%.1f / 100", J.d(s, "overall")), "stat-value"), UI.label("Circular Value Score", "stat-title"));
        String[][] f = {{"Functionality", "functionality"}, {"Physical condition", "condition"}, {"Repairability", "repairability"}, {"Reusability", "reusability"}, {"Component recovery potential", "componentRecovery"}, {"Estimated remaining useful life", "remainingLife"}};
        for (String[] x : f) { ProgressBar b = new ProgressBar(J.d(s, x[1]) / 100.0); b.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(b, Priority.ALWAYS);
            Label lbl = UI.label(x[0], "label"); lbl.setMinWidth(230); v.getChildren().add(new HBox(10, lbl, b, UI.label(String.format("%.0f", J.d(s, x[1])), "muted"))); }
        v.getChildren().addAll(UI.row(UI.label("Recommended pathway:", "section-title"), UI.badge(J.s(s, "recommendedPathway"))), UI.label(J.s(s, "explanation"), "label"),
                UI.label("Rule-based estimate from the questionnaire answers, not a physical test. Weights and thresholds are configurable by admins.", "muted"));
        return UI.scroll(v);
    }

    private Node safety(JsonNode l) {
        VBox v = new VBox(10); v.setPadding(new Insets(16));
        v.getChildren().addAll(UI.row(UI.label("Hazard level", "section-title"), UI.badge(J.s(l, "hazardLevel"))),
                UI.label("Normal shipping: " + (J.b(l, "shippingBlocked") ? "BLOCKED. Specialist handler required: " + J.s(l, "specialistRequired") : "allowed"), "label"),
                UI.label("Data sanitization: " + (!J.b(l, "requiresSanitization") ? "not required" : J.b(l, "sanitizationVerified") ? "VERIFIED, certificate issued" : "required, not yet verified"), "label"),
                UI.label("The hazard level comes from the seller's inspection questionnaire and admin-configured rules. It is a declared self-assessment, not a hardware diagnostic or image analysis.", "muted"));
        boolean owner = Session.userId != null && Session.userId == J.l(l, "sellerId");
        if (owner || Session.is("RESOLVER")) {
            VBox hist = new VBox(8); v.getChildren().addAll(UI.label("Inspection history", "section-title"), hist);
            Async.run(() -> ApiClient.get("/api/listings/" + id + "/hazard"), arr -> { for (JsonNode h : arr) {
                VBox c = UI.card(UI.row(UI.badge(J.s(h, "level")), UI.label(J.s(h, "totalPoints") + " risk points  |  " + J.date(h, "createdAt"), "muted")));
                for (JsonNode f : h.path("findings")) c.getChildren().add(UI.label("- " + f.asText(), "label"));
                if (h.path("handling").size() > 0) { c.getChildren().add(UI.label("Safe handling", "section-title")); for (JsonNode f : h.path("handling")) c.getChildren().add(UI.label("- " + f.asText(), "label")); }
                hist.getChildren().add(c); } });
        }
        return UI.scroll(v);
    }
}
