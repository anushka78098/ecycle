package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.component.*;
import com.ecycle.frontend.util.Alerts;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

/** QR-based chain of custody: scan (image or typed code), view the tamper-evident SHA-256 hash chain, and record handovers. */
public final class CustodyViews {
    private CustodyViews() {}

    public static Node scan() { return scan(null); }
    public static Node scan(String initialCode) {
        TextField code = new TextField(initialCode); code.setPromptText("Asset code or QR payload (ecycle://passport/EW-...)"); HBox.setHgrow(code, Priority.ALWAYS);
        TextField where = new TextField(); where.setPromptText("Location (optional)"); where.setPrefWidth(200);
        VBox result = new VBox(10);
        Runnable[] lookup = new Runnable[1];
        Button scanBtn = UI.primary("Scan / record scan", () -> { if (!code.getText().isBlank()) { var b = J.obj(); b.put("payload", code.getText().trim()); if (!where.getText().isBlank()) b.put("location", where.getText().trim()); Async.run(() -> ApiClient.post("/api/custody/scan", b), c -> show(result, c, code)); } });
        Button view = UI.secondary("View chain only", () -> { String c = assetCode(code.getText()); if (!c.isBlank()) Async.run(() -> ApiClient.get("/api/custody/" + c), j -> show(result, j, code)); });
        Button img = UI.secondary("Scan QR image...", () -> { FileChooser fc = new FileChooser(); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg")); File f = fc.showOpenDialog(null); if (f == null) return;
            try { code.setText(decode(f)); } catch (Exception e) { Alerts.info("No QR code could be read from that image."); } });
        VBox root = new VBox(12, UI.pageHeader("Chain of custody", "Every scan and handover is appended to a per-asset, timestamped log. Each entry stores the SHA-256 hash of the previous one, so edits or deletions are detected."),
                new HBox(8, code, where, img), new HBox(8, scanBtn, view), result);
        root.setPadding(new Insets(20));
        if (initialCode != null && !initialCode.isBlank()) Async.run(() -> ApiClient.get("/api/custody/" + assetCode(initialCode)), j -> show(result, j, code));
        return UI.scroll(root);
    }

    static String assetCode(String payload) { String p = payload == null ? "" : payload.trim(); int i = p.lastIndexOf('/'); return (i >= 0 ? p.substring(i + 1) : p).trim(); }

    static String decode(File f) throws Exception {
        var img = javax.imageio.ImageIO.read(f);
        return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img)))).getText();
    }

    public static void show(VBox target, JsonNode chain, TextField code) {
        target.getChildren().clear();
        boolean ok = J.b(chain, "valid");
        Label banner = UI.label((ok ? "Chain verified: " : "TAMPERING DETECTED: ") + J.s(chain, "verificationMessage") + "   (" + J.l(chain, "length") + " entries)", ok ? "badge-green" : "badge-red"); banner.setPadding(new Insets(8));
        target.getChildren().addAll(UI.label(J.s(chain, "assetName") + "  -  " + J.s(chain, "assetCode"), "page-title"), banner,
                UI.label("Current custodian: " + J.s(chain, "currentCustodianLabel") + "\nHead hash: " + J.s(chain, "headHash"), "muted"));
        List<EventTimeline.Row> rows = new ArrayList<>();
        for (JsonNode e : chain.path("entries")) rows.add(new EventTimeline.Row("#" + J.l(e, "seq") + " " + J.s(e, "eventType"),
                J.date(e, "occurredAt") + "  |  " + J.s(e, "actorLabel") + " (" + J.s(e, "actorRole") + ")" + (J.s(e, "location").isBlank() ? "" : "  |  " + J.s(e, "location")) + "  |  custodian: " + J.s(e, "custodianLabel"),
                (J.s(e, "detail").isBlank() ? "" : J.s(e, "detail") + "\n") + "hash " + J.s(e, "hash").substring(0, Math.min(16, J.s(e, "hash").length())) + "...  prev " + J.s(e, "prevHash").substring(0, Math.min(16, J.s(e, "prevHash").length())) + "..."));
        target.getChildren().add(new EventTimeline(rows));
        String c = J.s(chain, "assetCode");
        target.getChildren().add(UI.secondary("Record handover / inspection...", () -> FormDialog.show("Record custody event", "Record", List.of(
                FormDialog.combo("eventType", "Event", true, FormDialog.opts("HANDOVER", "RECEIVED", "INSPECTED", "STORED", "TRANSPORT_STARTED", "TRANSPORT_ENDED", "NOTE"), "RECEIVED"),
                FormDialog.integer("toUserId", "Hand over to user id (HANDOVER only)", false, null), FormDialog.text("location", "Location", false), FormDialog.area("detail", "Details", false)),
                b -> () -> ApiClient.post("/api/custody/" + c + "/events", b), r -> show(target, r, code))));
    }
}
