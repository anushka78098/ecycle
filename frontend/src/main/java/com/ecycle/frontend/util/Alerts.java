package com.ecycle.frontend.util;

import com.ecycle.frontend.api.ApiError;
import com.ecycle.frontend.api.Session;
import com.ecycle.frontend.navigation.Nav;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Alerts {
    private static final Logger log = LoggerFactory.getLogger(Alerts.class);
    private Alerts() {}

    private static Alert make(Alert.AlertType t, String header, String text) {
        Alert a = new Alert(t, text, ButtonType.OK); a.setHeaderText(header); a.getDialogPane().setMinWidth(420);
        a.getDialogPane().getStylesheets().add(Nav.css()); return a;
    }
    public static void error(Throwable t) {
        log.warn("Operation failed: {}", t.toString());
        Runnable r = () -> {
            if (t instanceof ApiError e && e.status() == 401 && Session.active()) { Session.clear(); Nav.showLogin(); info("Your session ended. Please sign in again."); return; }
            make(Alert.AlertType.ERROR, "Something went wrong", t instanceof ApiError e ? e.display() : String.valueOf(t.getMessage())).show();
        };
        if (Platform.isFxApplicationThread()) r.run(); else Platform.runLater(r);
    }
    public static void info(String text) { make(Alert.AlertType.INFORMATION, null, text).show(); }
    public static void confirm(String text, Runnable onYes) {
        Alert a = make(Alert.AlertType.CONFIRMATION, "Please confirm", text); a.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        a.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> onYes.run());
    }
}
