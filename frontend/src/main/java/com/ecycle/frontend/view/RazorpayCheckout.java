package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.navigation.Nav;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.function.BiConsumer;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

/**
 * Razorpay Standard Checkout inside a JavaFX WebView (the one place a WebView is genuinely needed: the provider's hosted checkout is JavaScript).
 * The page only reports payment id + signature back to Java; the Spring backend then verifies the signature. Success shown in the page is never trusted.
 */
public final class RazorpayCheckout {
    private RazorpayCheckout() {}

    public static final class Bridge {
        private final BiConsumer<String, String> onSuccess; private final Runnable onDismiss;
        Bridge(BiConsumer<String, String> s, Runnable d) { onSuccess = s; onDismiss = d; }
        public void success(String paymentId, String signature) { javafx.application.Platform.runLater(() -> onSuccess.accept(paymentId, signature)); }
        public void dismissed() { javafx.application.Platform.runLater(onDismiss); }
    }

    public static void open(JsonNode init, BiConsumer<String, String> onSuccess) {
        ObjectNode o = ApiClient.mapper().createObjectNode();
        o.put("key", init.path("keyId").asText()); o.put("order_id", init.path("providerOrderId").asText()); o.put("currency", init.path("currency").asText("INR"));
        o.put("amount", new BigDecimal(init.path("amount").asText()).movePointRight(2).longValue()); o.put("name", "E-CYCLE"); o.put("description", "Order payment");
        String html = "<html><body style='font-family:sans-serif;text-align:center;padding:40px'><p>Opening secure checkout...</p>"
                + "<script src='https://checkout.razorpay.com/v1/checkout.js'></script><script>"
                + "var opts=" + o.toString().replace("</", "<\\/") + ";"
                + "opts.handler=function(r){window.java.success(r.razorpay_payment_id,r.razorpay_signature);};"
                + "opts.modal={ondismiss:function(){window.java.dismissed();}};"
                + "function go(){new Razorpay(opts).open();} window.onload=go;</script></body></html>";
        WebView wv = new WebView(); WebEngine eng = wv.getEngine();
        Dialog<ButtonType> d = new Dialog<>(); d.setTitle("Razorpay checkout"); d.getDialogPane().getStylesheets().add(Nav.css());
        Bridge bridge = new Bridge((p, s) -> { d.close(); onSuccess.accept(p, s); }, d::close);
        eng.getLoadWorker().stateProperty().addListener((obs, a, b) -> { if (b == javafx.concurrent.Worker.State.SUCCEEDED) ((JSObject) eng.executeScript("window")).setMember("java", bridge); });
        eng.loadContent(html);
        wv.setPrefSize(520, 700); d.getDialogPane().setContent(wv); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.show();
    }
}
