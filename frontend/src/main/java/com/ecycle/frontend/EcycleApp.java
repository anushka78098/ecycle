package com.ecycle.frontend;

import com.ecycle.frontend.navigation.Nav;
import javafx.application.Application;
import javafx.stage.Stage;

public class EcycleApp extends Application {
    @Override
    public void start(Stage stage) {
        System.setProperty("http.agent", "E-CYCLE-Desktop/1.0 (educational marketplace client)");   // OpenStreetMap tile policy requires an identifying User-Agent
        stage.setMinWidth(1100); stage.setMinHeight(720);
        Nav.init(stage); Nav.showLogin(); stage.show();
    }
}
