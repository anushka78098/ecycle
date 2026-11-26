package com.ecycle.frontend;

/** Plain main class so the fat jar starts without needing JavaFX on the module path. */
public final class Launcher {
    private Launcher() {}
    public static void main(String[] args) { javafx.application.Application.launch(EcycleApp.class, args); }
}
