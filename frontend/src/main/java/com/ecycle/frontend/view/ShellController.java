package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.api.LiveClient;
import com.ecycle.frontend.api.Session;
import com.ecycle.frontend.component.Motion;
import com.ecycle.frontend.component.ResourceView;
import com.ecycle.frontend.component.UI;
import com.ecycle.frontend.navigation.Menus;
import com.ecycle.frontend.navigation.Nav;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.KeyFrame;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Role shell: sidebar navigation, top bar (search, notification centre, profile menu) and a content area. All data loads are asynchronous. */
public class ShellController {
    private static final Logger log = LoggerFactory.getLogger(ShellController.class);
    public static ShellController current;
    @FXML private Label brandLabel; @FXML private VBox navBox; @FXML private StackPane content; @FXML private Label roleLabel, userLabel;
    @FXML private TextField search; @FXML private Button bell; @FXML private MenuButton profileMenu; @FXML private ComboBox<String> roleSwitch;
    private javafx.animation.Timeline poll; private Button activeBtn; private Runnable unlisten;

    @FXML private void initialize() {
        current = this;
        Motion.pulseGlow(brandLabel, javafx.scene.paint.Color.web("#22d3ee"));
        profileMenu.setText(Session.name); userLabel.setText(Session.name + "\n" + Session.email);
        if (Session.roles.size() > 1) {
            roleSwitch.getItems().addAll(Session.roles); roleSwitch.setValue(Session.activeRole); roleSwitch.setVisible(true); roleSwitch.setManaged(true);
            roleSwitch.setOnAction(e -> { Session.activeRole = roleSwitch.getValue(); buildNav(); });
        }
        search.setOnAction(e -> { if (Session.is("BUYER")) show(new MarketplaceView(search.getText())); });
        search.setVisible(Session.is("BUYER")); search.setManaged(Session.is("BUYER"));
        buildNav();
        poll = new javafx.animation.Timeline(new KeyFrame(Duration.seconds(60), e -> refreshBell()));
        poll.setCycleCount(javafx.animation.Animation.INDEFINITE); poll.play(); refreshBell();
        LiveClient.start();   // real-time pushes (notifications, pickup status) over STOMP/WebSocket; polling above is only a fallback
        unlisten = LiveClient.listen(ev -> {
            if (!"NOTIFICATION".equals(ev.path("type").asText())) return;
            refreshBell();
            JsonNode notification = ev.path("payload");
            if ("CHAT".equals(J.s(notification, "type"))) {
                bell.setTooltip(new Tooltip(J.s(notification, "title") + "\n" + J.s(notification, "body")));
            }
        });
    }

    private void buildNav() {
        search.setVisible(Session.is("BUYER")); search.setManaged(Session.is("BUYER"));
        navBox.getChildren().clear(); roleLabel.setText(Session.activeRole + " WORKSPACE");
        boolean first = true;
        for (Menus.Entry e : Menus.forRole(Session.activeRole)) {
            if (e.view() == null) { Label l = UI.label(e.label().toUpperCase(), "nav-section"); navBox.getChildren().add(l); continue; }
            Button b = new Button(e.label()); b.getStyleClass().add("nav-btn"); b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(a -> { activate(b); show(e.view().get()); });
            navBox.getChildren().add(b);
            if (first) { first = false; activate(b); show(e.view().get()); }
        }
    }
    private void activate(Button b) {
        if (activeBtn != null) activeBtn.getStyleClass().remove("nav-btn-active");
        activeBtn = b; if (!b.getStyleClass().contains("nav-btn-active")) b.getStyleClass().add("nav-btn-active");
    }
    public void show(Node n) { content.getChildren().setAll(n); Motion.enter(n); }   // fade + slide between screens

    private void refreshBell() {
        Async.run(() -> ApiClient.get("/api/notifications/unread-count").asLong(), n -> {
            bell.setText(n > 0 ? "Notifications (" + n + ")" : "Notifications");
            bell.getStyleClass().remove("icon-btn-alert"); if (n > 0) bell.getStyleClass().add("icon-btn-alert");
        }, t -> log.warn("Notification poll failed: {}", t.getMessage()));
    }
    @FXML private void onBell() {
        ResourceView v = new ResourceView("Notification centre", "Order, payment, auction, dispute and verification updates", () -> ApiClient.get("/api/notifications"));
        v.date("When", "createdAt").col("Title", "title").col("Details", "body").col("Type", n -> J.human(J.s(n, "type")))
                .col("Read", n -> J.b(n, "read") ? "Yes" : "No")
                .action("Mark read", n -> !J.b(n, "read"), n -> Async.run(() -> ApiClient.post("/api/notifications/" + J.s(n, "id") + "/read", null), r -> { v.reload(); refreshBell(); }))
                .tool("Mark all read", () -> Async.run(() -> ApiClient.post("/api/notifications/read-all", null), r -> { v.reload(); refreshBell(); }));
        Dialog<ButtonType> d = new Dialog<>(); d.setTitle("Notifications"); d.getDialogPane().getStylesheets().add(Nav.css());
        v.setPrefSize(900, 520); d.getDialogPane().setContent(v); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.setResizable(true); d.show();
    }
    @FXML private void onProfile() { show(new ProfileView()); }
    @FXML private void onLogout() {
        if (poll != null) poll.stop();
        if (unlisten != null) unlisten.run();
        LiveClient.stop();
        Async.run(() -> ApiClient.post("/api/auth/logout", null), r -> done(), t -> { log.warn("Server logout failed: {}", t.getMessage()); done(); });
    }
    private void done() { Session.clear(); current = null; Nav.showLogin(); }
}
