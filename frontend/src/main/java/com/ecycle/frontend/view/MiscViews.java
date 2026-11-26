package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.api.LiveClient;
import com.ecycle.frontend.api.Session;
import com.ecycle.frontend.component.*;
import com.ecycle.frontend.navigation.Nav;
import com.ecycle.frontend.util.Alerts;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.ecycle.shared.Enums;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Wishlist, requirements, recommendations, offers, messaging and reviews. */
public final class MiscViews {
    private MiscViews() {}

    public static ResourceView wishlist() {
        ResourceView v = new ResourceView("Wishlist", "Products you saved", () -> ApiClient.get("/api/wishlist"));
        v.col("Product", "name").col("Category", "categoryName").money("Price", "expectedPrice").badge("Condition", "physicalCondition").col("Score", n -> String.format("%.0f", J.d(n, "score.overall"))).badge("Status", "status").col("City", "pickupCity")
                .action("Open", n -> true, n -> new ProductDialog(J.l(n, "id")).show()).onOpen(n -> new ProductDialog(J.l(n, "id")).show())
                .action("Remove", "btn-danger", n -> true, n -> Async.run(() -> ApiClient.delete("/api/wishlist/" + J.l(n, "id")), r -> v.reload()));
        return v;
    }

    public static ResourceView myOffers() {
        ResourceView v = new ResourceView("My offers", "Track offers you made. Accepted offers become orders in My orders.", () -> ApiClient.get("/api/offers/mine"));
        v.col("Offer", "id").col("Product", "listingName").money("Asking", "askingPrice").money("My offer", "amount").badge("Status", "status").date("Sent", "createdAt")
                .action("Withdraw", "btn-danger", n -> "PENDING".equals(J.s(n, "status")), n -> Async.run(() -> ApiClient.post("/api/offers/" + J.l(n, "id") + "/withdraw", null), r -> v.reload()))
                .action("View product", n -> true, n -> new ProductDialog(J.l(n, "listingId")).show());
        return v;
    }

    public static ResourceView requirements() {
        ResourceView v = new ResourceView("My requirements", "Describe what you need; the matching engine ranks live listings against it", () -> ApiClient.get("/api/requirements"));
        v.col("#", "id").col("Category", n -> J.s(n, "categoryName").isBlank() ? "Any" : J.s(n, "categoryName")).money("Max budget", "maxBudget").col("City", "city").col("Min condition", n -> J.human(J.s(n, "minCondition"))).col("Intended use", "intendedUse")
                .col("Components", "requiredComponents").col("Pathway", n -> J.human(J.s(n, "preferredPathway"))).yesNo("Active", "active")
                .tool("New requirement", () -> form(null, v)).action("Edit", n -> true, n -> form(n, v))
                .action("Delete", "btn-danger", n -> true, n -> Async.run(() -> ApiClient.delete("/api/requirements/" + J.l(n, "id")), r -> v.reload()));
        return v;
    }
    private static void form(JsonNode n, ResourceView v) {
        Async.run(() -> ApiClient.get("/api/categories"), cats -> {
            List<FormDialog.Opt> o = new ArrayList<>(); o.add(new FormDialog.Opt("", "Any category")); cats.forEach(c -> o.add(new FormDialog.Opt(c.path("id").asText(), c.path("name").asText()))); boolean e = n != null;
            FormDialog.show(e ? "Edit requirement" : "New requirement", "Save", List.of(FormDialog.combo("categoryId", "Category", false, o, e ? J.s(n, "categoryId") : ""), FormDialog.num("maxBudget", "Maximum budget (INR)", false, e ? J.s(n, "maxBudget") : null),
                    FormDialog.text("city", "Preferred city", false, e ? J.s(n, "city") : null), FormDialog.combo("minCondition", "Minimum condition", false, FormDialog.enumOpts(Enums.ItemCondition.class, true), e ? J.s(n, "minCondition") : ""),
                    FormDialog.text("intendedUse", "Intended use (keywords: office work, gaming, spare parts)", false, e ? J.s(n, "intendedUse") : null), FormDialog.text("requiredComponents", "Required components (keywords: ssd, 16gb ram)", false, e ? J.s(n, "requiredComponents") : null),
                    FormDialog.combo("pathway", "Preferred pathway", false, FormDialog.enumOpts(Enums.Pathway.class, true), e ? J.s(n, "preferredPathway") : ""), FormDialog.check("active", "Active", !e || J.b(n, "active"))),
                    b -> () -> { if (b.has("categoryId")) b.put("categoryId", b.get("categoryId").asLong()); if (b.has("pathway")) { b.put("preferredPathway", b.get("pathway").asText()); b.remove("pathway"); }
                        return e ? ApiClient.put("/api/requirements/" + J.l(n, "id"), b) : ApiClient.post("/api/requirements", b); }, r -> v.reload());
        });
    }

    /** Ranked recommendations with the explanation of every point awarded. */
    public static Node recommendations() {
        VBox root = new VBox(12); root.setPadding(new Insets(20));
        FlowPane grid = UI.flow(16);
        root.getChildren().addAll(UI.pageHeader("Recommended for you", "Ranked by the matching engine: category, budget, location, condition, intended use, components and pathway"), UI.scroll(grid));
        VBox.setVgrow(root.getChildren().get(1), Priority.ALWAYS);
        Async.run(() -> ApiClient.get("/api/recommendations?limit=18"), recs -> {
            if (recs.isEmpty()) grid.getChildren().add(UI.label("Nothing to recommend yet. Add a requirement or save products to your wishlist.", "muted"));
            for (JsonNode r : recs) {
                JsonNode l = r.path("listing"); VBox c = new VBox(8);
                c.getChildren().add(new ProductCard(l, n -> new ProductDialog(J.l(l, "id")).show(), s -> { }, false, () -> Async.run(() -> ApiClient.post("/api/wishlist/" + J.l(l, "id"), null), x -> Alerts.info("Saved to wishlist."))));
                VBox why = UI.card(UI.label(String.format("Match %.1f / 100  |  %s", J.d(r, "matchScore"), J.s(r, "basedOn")), "section-title")); why.setPrefWidth(250); why.setMaxWidth(250);
                for (JsonNode s : r.path("reasons")) why.getChildren().add(UI.label("- " + s.asText(), "muted"));
                c.getChildren().add(why); grid.getChildren().add(c);
            }
        });
        return root;
    }

    public static ResourceView messages() {
        ResourceView v = new ResourceView("Messages", "Conversations about listings between buyers and sellers", () -> ApiClient.get("/api/chat/conversations"));
        v.col("Product", "listingName").col("From", "senderName").col("To", "recipientName").col("Latest message", "body").date("When", "createdAt")
                .action("Open conversation", n -> true, n -> thread(n)).onOpen(MiscViews::thread);
        Runnable[] off = {null};
        v.sceneProperty().addListener((o, oldScene, scene) -> {
            if (scene != null && off[0] == null) {
                off[0] = LiveClient.listen(ev -> {
                    if ("CHAT".equals(ev.path("type").asText())) v.reload();
                });
            } else if (scene == null && off[0] != null) {
                off[0].run();
                off[0] = null;
            }
        });
        return v;
    }
    private static void thread(JsonNode m) {
        long listing = J.l(m, "listingId"), other = J.l(m, "senderId") == Session.userId ? J.l(m, "recipientId") : J.l(m, "senderId");
        String otherName = J.l(m, "senderId") == Session.userId ? J.s(m, "recipientName") : J.s(m, "senderName");
        VBox transcript = new VBox(10); transcript.setPadding(new Insets(14));
        ScrollPane scroll = UI.scroll(transcript); VBox.setVgrow(scroll, Priority.ALWAYS);
        TextField in = new TextField(); in.setPromptText("Write a message..."); HBox.setHgrow(in, Priority.ALWAYS);
        Button sendButton = UI.primary("Send", () -> { });
        Set<String> seen = new HashSet<>();
        java.util.function.Consumer<JsonNode> append = message -> {
            String id = J.s(message, "id");
            if (id.isBlank() || !seen.add(id)) return;
            if (transcript.getChildren().size() == 1 && transcript.getChildren().get(0) instanceof Label) transcript.getChildren().clear();
            boolean mine = J.l(message, "senderId") == Session.userId;
            VBox bubble = new VBox(4, UI.label(mine ? "You  ·  " + J.date(message, "createdAt") : J.s(message, "senderName") + "  ·  " + J.date(message, "createdAt"), "chat-meta"),
                    UI.label(J.s(message, "body"), "chat-body"));
            bubble.getStyleClass().addAll("chat-bubble", mine ? "chat-bubble-own" : "chat-bubble-other");
            bubble.setMaxWidth(440);
            HBox row = new HBox(8);
            row.setAlignment(mine ? javafx.geometry.Pos.CENTER_RIGHT : javafx.geometry.Pos.CENTER_LEFT);
            if (mine) row.getChildren().addAll(UI.grow(), bubble); else row.getChildren().add(bubble);
            transcript.getChildren().add(row);
            Platform.runLater(() -> scroll.setVvalue(1));
        };
        Runnable[] load = new Runnable[1];
        load[0] = () -> Async.run(() -> ApiClient.get("/api/chat/thread?listingId=" + listing + "&with=" + other), messages -> {
            transcript.getChildren().clear();
            seen.clear();
            messages.forEach(append);
            if (messages.isEmpty()) transcript.getChildren().add(UI.label("Start the conversation about this listing.", "muted"));
            Platform.runLater(() -> scroll.setVvalue(1));
        });
        sendButton.setOnAction(e -> {
            String body = in.getText().trim();
            if (body.isEmpty() || sendButton.isDisabled()) return;
            var request = J.obj(); request.put("listingId", listing); request.put("recipientId", other); request.put("body", body);
            sendButton.setDisable(true);
            Async.run(() -> ApiClient.post("/api/chat", request), sent -> {
                in.clear();
                sendButton.setDisable(false);
                append.accept(sent);
            }, error -> {
                sendButton.setDisable(false);
                Alerts.error(error);
            });
        });
        in.setOnAction(e -> sendButton.fire());
        Dialog<ButtonType> d = new Dialog<>(); d.setTitle(J.s(m, "listingName")); d.getDialogPane().getStylesheets().add(Nav.css());
        d.getDialogPane().setContent(new VBox(10, UI.label("Chat with " + otherName, "section-title"), scroll, new HBox(8, in, sendButton)));
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.getDialogPane().setPrefSize(660, 520); d.setResizable(true);
        Runnable off = LiveClient.listen(ev -> {
            if (!"CHAT".equals(ev.path("type").asText())) return;
            JsonNode message = ev.path("payload");
            if (J.l(message, "listingId") != listing) return;
            long sender = J.l(message, "senderId"), recipient = J.l(message, "recipientId");
            if ((sender == Session.userId && recipient == other) || (sender == other && recipient == Session.userId)) append.accept(message);
        });
        d.setOnHidden(e -> off.run());
        d.show(); load[0].run();
    }

    public static ResourceView myReviews() {
        return new ResourceView("Reviews of me", "Ratings from your completed orders (they drive your seller trust score)", () -> ApiClient.get("/api/reviews/user/" + Session.userId))
                .col("Order", "orderId").col("From", "reviewerName").col("Rating", n -> "*".repeat(Math.max(0, (int) J.l(n, "rating"))) + " (" + J.l(n, "rating") + "/5)").col("Comment", "comment").date("When", "createdAt");
    }
}
