package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.api.Session;
import com.ecycle.frontend.component.FormDialog;
import com.ecycle.frontend.component.UI;
import com.ecycle.frontend.util.Alerts;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;

public class ProfileView extends VBox {
    public ProfileView() {
        setSpacing(12); setPadding(new Insets(20)); getChildren().add(UI.label("Loading...", "muted")); load();
    }
    private void load() { Async.run(() -> ApiClient.get("/api/profile"), this::render); }
    private void render(JsonNode p) {
        VBox card = UI.card(UI.label(J.s(p, "fullName"), "page-title"), UI.label(J.s(p, "email") + "  |  roles: " + String.join(", ", Session.roles), "muted"),
                UI.row(UI.label("Verification:", "label"), UI.badge(J.s(p, "verificationStatus").isBlank() ? "NONE" : J.s(p, "verificationStatus"))),
                UI.label("City: " + J.s(p, "city") + "   |   Organization: " + J.s(p, "organization") + "   |   Phone: " + J.s(p, "phone"), "label"));
        if (J.s(p, "buyerType").length() > 0) card.getChildren().add(UI.label("Buyer type: " + J.human(J.s(p, "buyerType")) + "   |   Verified recovery capability: " + J.s(p, "recoveryCapability") + "   |   Environmental rating: " + J.s(p, "environmentalRating"), "label"));
        if (Session.is("SELLER")) card.getChildren().add(UI.label("Seller trust score (from reviews): " + String.format("%.0f", J.d(p, "trustScore")) + "/100", "label"));
        card.getChildren().add(UI.row(UI.primary("Edit profile", () -> FormDialog.show("Edit profile", "Save", List.of(FormDialog.text("fullName", "Full name", true, J.s(p, "fullName")), FormDialog.text("phone", "Phone", false, J.s(p, "phone")),
                        FormDialog.text("city", "City", false, J.s(p, "city")), FormDialog.text("organization", "Organization / business", false, J.s(p, "organization"))),
                b -> () -> ApiClient.put("/api/profile", b), r -> { Session.name = J.s(r, "fullName"); load(); })),
                UI.secondary("Change password", () -> FormDialog.show("Change password", "Change", List.of(FormDialog.pass("currentPassword", "Current password"), FormDialog.pass("newPassword", "New password (min 8)")),
                        b -> () -> ApiClient.post("/api/profile/password", b), r -> Alerts.info("Password changed.")))));
        getChildren().setAll(UI.pageHeader("My profile", "Account details and verification status"), card);
    }
}
