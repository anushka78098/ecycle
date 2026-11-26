package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.component.UI;
import com.ecycle.frontend.util.Alerts;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Chat with the Eco-Assistant (LangChain4j on the server). Actions that change data appear as cards the user must confirm; the model cannot confirm them itself. */
public final class AssistantView extends BorderPane {
    private final VBox messages = new VBox(10); private final ScrollPane scroll = new ScrollPane(messages);
    private final TextField input = new TextField(); private final Button send = UI.primary("Send", this::send);
    private final ListView<JsonNode> convs = new ListView<>(); private final Label mode = UI.label("", "muted");
    private String conversationId;

    public AssistantView() {
        setPadding(new Insets(20));
        Button fresh = UI.secondary("New chat", this::newChat);
        setTop(new VBox(6, UI.pageHeader("Eco-Assistant", "Ask about e-waste, your eco-points, pickups, certificates and rewards. Booking, cancelling and redeeming always need your confirmation.", fresh), mode));
        convs.setPrefWidth(230); convs.setCellFactory(l -> new ListCell<>() { @Override protected void updateItem(JsonNode n, boolean e) { super.updateItem(n, e); setText(e || n == null ? null : J.s(n, "title")); } });
        convs.getSelectionModel().selectedItemProperty().addListener((o, a, n) -> { if (n != null) open(J.s(n, "id")); });
        messages.setPadding(new Insets(10)); scroll.setFitToWidth(true); scroll.getStyleClass().add("clear-scroll"); messages.heightProperty().addListener((o, a, b) -> scroll.setVvalue(1.0));
        input.setPromptText("Ask something, e.g. \"How many eco-points do I have?\""); HBox.setHgrow(input, Priority.ALWAYS); input.setOnAction(e -> send());
        HBox bar = new HBox(8, input, send); bar.setPadding(new Insets(10, 0, 0, 0));
        setLeft(convs); BorderPane.setMargin(convs, new Insets(10, 12, 0, 0)); setCenter(scroll); setBottom(bar);
        add("assistant", "Hi! I can check your eco-points, track pickups, find recyclers, estimate your carbon impact and prepare bookings for you to confirm.", null);
        Async.run(() -> ApiClient.get("/api/assistant/status"), s -> mode.setText(s.path("mode").asText().equals("AI") ? "AI model: " + s.path("model").asText() : "Rule-based mode (no AI model configured on the server)"), t -> { });
        loadConversations();
    }

    private void loadConversations() { Async.run(() -> ApiClient.get("/api/assistant/conversations"), l -> { var items = FXCollections.<JsonNode>observableArrayList(); l.forEach(items::add); convs.setItems(items); }, t -> { }); }
    private void newChat() { conversationId = null; messages.getChildren().clear(); add("assistant", "New conversation started. How can I help?", null); convs.getSelectionModel().clearSelection(); }
    private void open(String id) {
        Async.run(() -> ApiClient.get("/api/assistant/conversations/" + id), h -> { conversationId = id; messages.getChildren().clear(); h.forEach(m -> add(J.s(m, "role"), J.s(m, "content"), null)); });
    }

    private void send() {
        String text = input.getText().trim(); if (text.isEmpty()) return;
        input.clear(); add("user", text, null); input.setDisable(true); send.setDisable(true);
        var body = J.obj(); body.put("message", text); if (conversationId != null) body.put("conversationId", conversationId);
        Async.run(() -> ApiClient.post("/api/assistant/chat", body), r -> {
            boolean first = conversationId == null; conversationId = J.s(r, "conversationId"); input.setDisable(false); send.setDisable(false); input.requestFocus();
            VBox extras = new VBox(6);
            for (JsonNode p : r.path("pendingActions")) extras.getChildren().add(actionCard(p));
            StringBuilder t = new StringBuilder(); r.path("toolsUsed").forEach(x -> t.append(t.length() == 0 ? "" : ", ").append(x.asText()));
            add("assistant", J.s(r, "reply"), extras.getChildren().isEmpty() ? null : extras);
            if (t.length() > 0) messages.getChildren().add(UI.label("Used: " + t + "  (" + J.s(r, "mode") + ")", "muted"));
            if (first) loadConversations();
        }, t -> { input.setDisable(false); send.setDisable(false); Alerts.error(t); });
    }

    private VBox actionCard(JsonNode p) {
        Label l = UI.label("Proposed action: " + J.s(p, "summary") + "\n(expires in 10 minutes)", "stat-title"); Label res = UI.label("", "muted");
        Button yes = UI.primary("Confirm", () -> { }), no = UI.secondary("Cancel", () -> { });
        yes.setOnAction(e -> { yes.setDisable(true); no.setDisable(true); Async.run(() -> ApiClient.post("/api/assistant/actions/" + J.s(p, "id") + "/confirm", null), r -> res.setText((J.b(r, "success") ? "Done: " : "Not done: ") + J.s(r, "message")), t -> { res.setText("Failed: " + t.getMessage()); }); });
        no.setOnAction(e -> { yes.setDisable(true); no.setDisable(true); Async.run(() -> ApiClient.post("/api/assistant/actions/" + J.s(p, "id") + "/cancel", null), r -> res.setText(J.s(r, "message")), t -> res.setText("Failed: " + t.getMessage())); });
        VBox card = UI.card(l, new HBox(8, yes, no), res); return card;
    }

    private void add(String role, String text, VBox extra) {
        boolean me = "user".equals(role);
        Label l = UI.label(text, me ? "badge-green" : "card"); l.setMaxWidth(640); l.setPadding(new Insets(10)); l.setTextOverrun(OverrunStyle.CLIP);
        VBox col = new VBox(6, l); if (extra != null) col.getChildren().add(extra);
        HBox row = new HBox(col); row.setAlignment(me ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT); messages.getChildren().add(row);
    }
}
