package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.component.FormDialog;
import com.ecycle.frontend.component.FormDialog.Opt;
import com.ecycle.frontend.component.UI;
import com.ecycle.frontend.util.Alerts;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.VBox;

/** Category-specific data-wipe checklist for a listing. Submitting it creates the evidence record the admin verifies; verification then issues the PDF certificate. */
public final class WipeDialog {
    private WipeDialog() {}

    public static void open(long listingId, Runnable done) {
        Async.run(() -> ApiClient.get("/api/listings/" + listingId + "/wipe-checklist"), t -> {
            List<Opt> methods = new ArrayList<>(); t.path("methods").forEach(m -> methods.add(new Opt(m.asText(), m.asText())));
            VBox checks = new VBox(6, UI.label("Tick each step you completed (* mandatory) - category: " + J.s(t, "categoryName"), "section-title"));
            List<CheckBox> boxes = new ArrayList<>();
            for (JsonNode i : t.path("items")) { CheckBox cb = new CheckBox(J.s(i, "text") + (J.b(i, "mandatory") ? " *" : "")); cb.setWrapText(true); cb.setUserData(J.s(i, "code")); boxes.add(cb); checks.getChildren().add(cb); }
            FormDialog.show("Data-wipe checklist", "Submit for verification", List.of(FormDialog.combo("method", "Sanitization method", true, methods, methods.isEmpty() ? "" : methods.get(0).value()),
                    FormDialog.text("toolName", "Tool / software used", false), FormDialog.text("deviceSerial", "Device serial / IMEI", false), FormDialog.text("evidenceRef", "Evidence reference (erase report id or file name)", true)), checks,
                    b -> { var body = b.deepCopy(); var arr = body.putArray("completedItems"); boxes.stream().filter(CheckBox::isSelected).forEach(c -> arr.add((String) c.getUserData())); return () -> ApiClient.post("/api/listings/" + listingId + "/wipe-checklist", body); },
                    r -> { Alerts.info("Checklist recorded and sent to an administrator for verification. A PDF certificate is issued once verified."); if (done != null) done.run(); });
        });
    }
}
