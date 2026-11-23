package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.api.ApiError;
import com.ecycle.frontend.api.GoogleSignIn;
import com.ecycle.frontend.api.Session;
import com.ecycle.frontend.navigation.Nav;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.ecycle.frontend.component.CodePanel;
import com.ecycle.frontend.component.Motion;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class LoginController {
    @FXML private TextField email, passwordShown;
    @FXML private PasswordField passwordHidden;
    @FXML private CheckBox showPassword;
    @FXML private Label error;
    @FXML private Button loginBtn, googleBtn;
    @FXML private ComboBox<String> googleRole;
    @FXML private VBox hero; @FXML private Label brand;

    @FXML private void initialize() {
        for (int i = 0; i < hero.getChildren().size(); i++) Motion.enter(hero.getChildren().get(i), 150.0 * i);   // staggered entrance
        hero.getChildren().add(new CodePanel());                                   // typing "terminal" window (has its own floating motion)
        googleRole.getItems().addAll("Buyer", "Seller"); googleRole.getSelectionModel().selectFirst();
        Motion.pulseGlow(brand, Color.web("#22d3ee")); Motion.pulseGlow(loginBtn, Color.web("#22d3ee"));
        passwordShown.textProperty().bindBidirectional(passwordHidden.textProperty());
        passwordShown.visibleProperty().bind(showPassword.selectedProperty());
        passwordHidden.visibleProperty().bind(showPassword.selectedProperty().not());
    }
    @FXML private void onLogin() {
        error.setText("");
        String e = email.getText() == null ? "" : email.getText().trim(), p = passwordHidden.getText();
        if (e.isEmpty() || p == null || p.isEmpty()) { error.setText("Enter your email and password."); return; }
        if (!e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) { error.setText("Enter a valid email address."); return; }
        loginBtn.setDisable(true);
        var body = J.obj(); body.put("email", e); body.put("password", p);
        Async.run(() -> ApiClient.post("/api/auth/login", body), res -> { Session.start(res); Nav.showShell(); },
                t -> { loginBtn.setDisable(false); error.setText(t instanceof ApiError a ? a.display() : "Could not sign in: " + t.getMessage()); });
    }
    /** Google sign-in: browser login (PKCE) -> ID token -> backend verifies it and returns our own JWT. New accounts join as the role chosen next to the button. */
    @FXML private void onGoogle() {
        error.setText(""); googleBtn.setDisable(true);
        String role = googleRole.getValue() == null ? "BUYER" : googleRole.getValue().toUpperCase();
        Async.run(() -> {
            String idToken = GoogleSignIn.obtainIdToken();
            var body = J.obj(); body.put("idToken", idToken); body.put("role", role);
            return ApiClient.post("/api/auth/google", body);
        }, res -> { Session.start(res); Nav.showShell(); },
           t -> { googleBtn.setDisable(false); error.setText(t instanceof ApiError a ? a.display() : "Google sign-in failed: " + t.getMessage()); });
    }
    @FXML private void onRegister() { Nav.showRegister(); }
    @FXML private void onForgot() { Nav.showForgot(); }
}
