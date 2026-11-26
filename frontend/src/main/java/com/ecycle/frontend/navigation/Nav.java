package com.ecycle.frontend.navigation;

import java.io.IOException;
import java.io.UncheckedIOException;
import com.ecycle.frontend.component.Motion;
import com.ecycle.frontend.component.TechBackground;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/** Top-level navigation between the auth screens and the role shell. */
public final class Nav {
    private Nav() {}
    private static Stage stage; private static Scene scene;

    public static void init(Stage s) { stage = s; }
    public static Stage stage() { return stage; }
    public static String css() { return Nav.class.getResource("/com/ecycle/frontend/css/ecycle.css").toExternalForm(); }

    private static void show(String fxml) {
        try {
            Parent root = FXMLLoader.load(Nav.class.getResource("/com/ecycle/frontend/fxml/" + fxml));
            Parent shown = root;
            if (!fxml.startsWith("shell")) { StackPane sp = new StackPane(new TechBackground(), root); sp.getStyleClass().add("auth-stack"); shown = sp; }   // animated backdrop behind the auth screens
            if (scene == null) { scene = new Scene(shown, 1280, 820); scene.getStylesheets().add(css()); stage.setScene(scene); } else scene.setRoot(shown);
            Motion.enter(root);
            stage.setTitle("E-CYCLE | Circular Electronics Marketplace");
        } catch (IOException e) { throw new UncheckedIOException("Cannot load " + fxml, e); }
    }
    public static void showLogin() { show("login.fxml"); }
    public static void showRegister() { show("register.fxml"); }
    public static void showForgot() { show("forgot.fxml"); }
    public static void showShell() { show("shell.fxml"); }
}
