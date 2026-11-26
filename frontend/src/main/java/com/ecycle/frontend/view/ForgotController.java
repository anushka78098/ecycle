package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.api.ApiError;
import com.ecycle.frontend.navigation.Nav;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class ForgotController {
    @FXML private TextField email, code;
    @FXML private PasswordField newPassword;
    @FXML private Label status;

    private void say(String s, boolean ok) { status.setText(s); status.getStyleClass().setAll("label", ok ? "ok-text" : "error-text"); }

    @FXML private void onSend() {
        if (!email.getText().trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) { say("Enter a valid email address.", false); return; }
        var b = J.obj(); b.put("email", email.getText().trim());
        Async.run(() -> ApiClient.post("/api/auth/forgot", b), r -> say(r.path("message").asText(), true), t -> say(t.getMessage(), false));
    }
    @FXML private void onReset() {
        if (code.getText().isBlank()) { say("Enter the reset code from your email.", false); return; }
        if (newPassword.getText().length() < 8) { say("New password must be at least 8 characters.", false); return; }
        var b = J.obj(); b.put("token", code.getText().trim()); b.put("newPassword", newPassword.getText());
        Async.run(() -> ApiClient.post("/api/auth/reset", b), r -> { say("Password updated. You can sign in now.", true); },
                t -> say(t instanceof ApiError a ? a.display() : t.getMessage(), false));
    }
    @FXML private void onBack() { Nav.showLogin(); }
}
