package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.api.ApiError;
import com.ecycle.frontend.api.Session;
import com.ecycle.frontend.component.FormDialog;
import com.ecycle.frontend.navigation.Nav;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.ecycle.shared.Enums;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class RegisterController {
    @FXML private ComboBox<FormDialog.Opt> role, buyerType;
    @FXML private TextField fullName, email, phone, city, organization, passwordShown;
    @FXML private PasswordField passwordHidden, confirm;
    @FXML private CheckBox showPassword;
    @FXML private Label error, orgLabel, typeLabel;

    @FXML private void initialize() {
        role.getItems().addAll(new FormDialog.Opt("SELLER", "Sell electronics / e-waste"), new FormDialog.Opt("BUYER", "Buy, refurbish or recycle"));
        role.setValue(role.getItems().get(1));
        buyerType.getItems().addAll(FormDialog.enumOpts(Enums.BuyerType.class, false)); buyerType.setValue(buyerType.getItems().get(0));
        role.valueProperty().addListener((o, a, b) -> refresh());
        passwordShown.textProperty().bindBidirectional(passwordHidden.textProperty());
        passwordShown.visibleProperty().bind(showPassword.selectedProperty());
        passwordHidden.visibleProperty().bind(showPassword.selectedProperty().not());
        refresh();
    }
    private void refresh() {
        boolean buyer = "BUYER".equals(role.getValue().value());
        orgLabel.setText(buyer ? "Organization (optional)" : "Business name (optional)");
        typeLabel.setVisible(buyer); typeLabel.setManaged(buyer); buyerType.setVisible(buyer); buyerType.setManaged(buyer);
    }
    @FXML private void onRegister() {
        error.setText("");
        String pw = passwordHidden.getText() == null ? "" : passwordHidden.getText();
        if (fullName.getText().isBlank()) { error.setText("Full name is required."); return; }
        if (!email.getText().trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) { error.setText("Enter a valid email address."); return; }
        if (pw.length() < 8 || !pw.matches(".*[A-Za-z].*") || !pw.matches(".*\\d.*")) { error.setText("Password needs at least 8 characters with letters and digits."); return; }
        if (!pw.equals(confirm.getText())) { error.setText("Passwords do not match."); return; }
        var b = J.obj(); b.put("email", email.getText().trim()); b.put("password", pw); b.put("fullName", fullName.getText().trim());
        b.put("role", role.getValue().value()); if (!phone.getText().isBlank()) b.put("phone", phone.getText().trim()); if (!city.getText().isBlank()) b.put("city", city.getText().trim());
        if ("SELLER".equals(role.getValue().value())) { if (!organization.getText().isBlank()) b.put("businessName", organization.getText().trim()); }
        else { if (!organization.getText().isBlank()) b.put("organization", organization.getText().trim()); b.put("buyerType", buyerType.getValue().value()); }
        Async.run(() -> ApiClient.post("/api/auth/register", b), res -> { Session.start(res); Nav.showShell(); },
                t -> error.setText(t instanceof ApiError a ? a.display() : "Could not register: " + t.getMessage()));
    }
    @FXML private void onBack() { Nav.showLogin(); }
}
