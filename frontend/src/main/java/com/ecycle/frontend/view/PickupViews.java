package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.api.LiveClient;
import com.ecycle.frontend.api.Session;
import com.ecycle.frontend.component.*;
import com.ecycle.frontend.component.FormDialog.Opt;
import com.ecycle.frontend.navigation.Nav;
import com.ecycle.frontend.util.Alerts;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Pickup booking follow-up and tracking. The tables refresh instantly when the server pushes a PICKUP event over WebSocket (no polling). */
public final class PickupViews {
    private PickupViews() {}

    private static void autoRefresh(Node view, Runnable reload) {
        Runnable[] off = new Runnable[1];
        view.sceneProperty().addListener((o, a, s) -> { if (s != null && off[0] == null) off[0] = LiveClient.listen(ev -> { if ("PICKUP".equals(ev.path("type").asText())) reload.run(); }); else if (s == null && off[0] != null) { off[0].run(); off[0] = null; } });
    }

    /** @param asRecycler true: jobs assigned to my facilities; false: pickups I requested (admins see everything). */
    public static ResourceView list(boolean asRecycler) {
        String as = asRecycler ? "recycler" : "requester";
        ResourceView v = new ResourceView(asRecycler ? "Pickup jobs" : "My pickups", asRecycler ? "Requests for your facilities. Status changes are pushed live to the requester." : "Track your e-waste pickups in real time",
                () -> ApiClient.get("/api/pickups?as=" + as));
        v.col("Tracking", "trackingCode").col("Items", n -> J.s(n, "itemsDescription") + " x" + J.l(n, "quantity")).col(asRecycler ? "From" : "Recycler", asRecycler ? "requesterName" : "recyclerName").col("Facility", "facilityName")
                .col("Scheduled", n -> J.date(n, J.s(n, "scheduledAt").isBlank() ? "preferredAt" : "scheduledAt")).col("Weight (kg)", n -> n.path("actualWeightKg").isNull() ? "~" + J.s(n, "estWeightKg") : J.s(n, "actualWeightKg")).badge("Status", "status")
                .action("Open / track", n -> true, n -> track(J.l(n, "id"), v)).onOpen(n -> track(J.l(n, "id"), v))
                .action("Update status", n -> n.path("nextActions").size() > 0, n -> updateDialog(n, v));
        autoRefresh(v, v::reload);
        return v;
    }

    private static List<Opt> nextOpts(JsonNode p) {
        List<Opt> o = new ArrayList<>(); p.path("nextActions").forEach(a -> { String s = a.asText(); if (!"VERIFY".equals(s)) o.add(new Opt(s, label(s))); }); return o;
    }
    private static String label(String s) { return switch (s) { case "ACCEPTED" -> "Accept"; case "REJECTED" -> "Decline"; case "EN_ROUTE" -> "On my way"; case "PICKED_UP" -> "Picked up"; case "AT_FACILITY" -> "Arrived at facility"; case "CANCELLED" -> "Cancel pickup"; default -> J.human(s); }; }

    private static void updateDialog(JsonNode p, ResourceView v) {
        boolean verify = false; for (JsonNode a : p.path("nextActions")) if ("VERIFY".equals(a.asText())) verify = true;
        List<Opt> opts = nextOpts(p);
        if (opts.isEmpty() && verify) { verifyDialog(p, v); return; }
        boolean needsScan = !J.s(p, "passportCode").isBlank();
        List<FormDialog.F> f = new ArrayList<>(List.of(FormDialog.combo("status", "New status", true, opts, opts.get(0).value()), FormDialog.text("note", "Note (optional)", false)));
        if (Session.userId != null && Session.userId == J.l(p, "recyclerId")) {
            f.add(FormDialog.text("scheduledDate", "Scheduled date when accepting (YYYY-MM-DD, optional)", false));
            if (needsScan) f.add(FormDialog.text("scanPayload", "Scan the item's QR (paste payload or asset code) - required for picked up / at facility", false, J.s(p, "passportCode")));
            f.add(FormDialog.num("lat", "My latitude (optional)", false, null)); f.add(FormDialog.num("lng", "My longitude (optional)", false, null));
        }
        final boolean verifyAvail = verify;
        FormDialog.show("Pickup " + J.s(p, "trackingCode"), "Update", f, b -> {
            ObjectNode body = b.deepCopy(); String d = body.path("scheduledDate").asText(""); body.remove("scheduledDate");
            if (!d.isBlank()) try { body.put("scheduledAt", LocalDate.parse(d).atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toString()); } catch (RuntimeException e) { return () -> { throw new com.ecycle.frontend.api.ApiError(400, "Date must look like 2026-12-01", null); }; }
            return () -> ApiClient.post("/api/pickups/" + J.l(p, "id") + "/status", body);
        }, r -> { v.reload(); if (verifyAvail && "AT_FACILITY".equals(r.path("status").asText())) Alerts.info("Now at the facility. Use 'Verify & complete' when the weight is measured."); });
    }

    private static void verifyDialog(JsonNode p, ResourceView v) {
        boolean data = J.b(p, "dataBearing");
        Async.run(() -> ApiClient.get("/api/pickups/" + J.l(p, "id") + "/wipe-checklist"), t -> {
            List<FormDialog.F> f = new ArrayList<>(List.of(FormDialog.num("actualWeightKg", "Measured weight (kg)", true, J.s(p, "estWeightKg")),
                    FormDialog.combo("treatment", "Treatment", true, FormDialog.opts("Dismantled and materials recovered", "Shredded and metals recovered", "Refurbished for reuse", "Hazardous components removed and processed"), "Dismantled and materials recovered"),
                    FormDialog.text("note", "Note", false)));
            VBox checks = new VBox(6);
            if (!J.s(p, "passportCode").isBlank()) f.add(FormDialog.text("scanPayload", "Scan the item's QR (payload or asset code)", true, J.s(p, "passportCode")));
            List<CheckBox> boxes = new ArrayList<>();
            if (data) {
                List<Opt> methods = new ArrayList<>(); t.path("methods").forEach(m -> methods.add(new Opt(m.asText(), m.asText())));
                f.add(FormDialog.combo("dataDestructionMethod", "Data destruction method", true, methods, methods.isEmpty() ? "" : methods.get(0).value())); f.add(FormDialog.text("deviceSerial", "Device serial / IMEI", false));
                checks.getChildren().add(UI.label("Data-wipe checklist (* mandatory) - tick every step you completed:", "section-title"));
                for (JsonNode i : t.path("items")) { CheckBox cb = new CheckBox(J.s(i, "text") + (J.b(i, "mandatory") ? " *" : "")); cb.setUserData(J.s(i, "code")); cb.setWrapText(true); boxes.add(cb); checks.getChildren().add(cb); }
            }
            FormDialog.show("Verify & complete " + J.s(p, "trackingCode"), "Complete and issue certificates", f, checks, b -> {
                ObjectNode body = b.deepCopy(); var arr = body.putArray("checklistItems"); boxes.stream().filter(CheckBox::isSelected).forEach(c -> arr.add((String) c.getUserData()));
                return () -> ApiClient.post("/api/pickups/" + J.l(p, "id") + "/verify", body);
            }, r -> { List<String> nos = new ArrayList<>(); r.path("certificateNos").forEach(c -> nos.add(c.asText())); Alerts.info("Pickup completed. Certificates issued: " + (nos.isEmpty() ? "see the Certificates screen" : String.join(", ", nos))); v.reload(); });
        });
    }

    /** Detail dialog with live status, location of the recycler and the event timeline. Refreshes itself on pushed events. */
    private static void track(long id, ResourceView parent) {
        Async.run(() -> ApiClient.get("/api/pickups/" + id), first -> {
            VBox body = new VBox(10); body.setPadding(new Insets(12));
            Dialog<ButtonType> d = new Dialog<>(); d.setTitle("Pickup " + J.s(first, "trackingCode")); d.getDialogPane().getStylesheets().add(Nav.css());
            Runnable[] render = new Runnable[1];
            render[0] = () -> Async.run(() -> ApiClient.get("/api/pickups/" + id), p -> {
                body.getChildren().clear();
                body.getChildren().add(UI.row(UI.label(J.s(p, "trackingCode"), "page-title"), UI.badge(J.s(p, "status")), UI.grow(), UI.label(LiveClient.connected() ? "Live" : "Live updates offline (use Refresh)", "muted")));
                body.getChildren().add(UI.label(J.s(p, "itemsDescription") + " x" + J.l(p, "quantity") + "  |  " + J.s(p, "facilityName") + "\nRecycler: " + J.s(p, "recyclerName") + "   Requester: " + J.s(p, "requesterName")
                        + "\nPreferred: " + J.date(p, "preferredAt") + (J.s(p, "scheduledAt").isBlank() ? "" : "   Scheduled: " + J.date(p, "scheduledAt")) + "\nEstimated weight: " + J.s(p, "estWeightKg") + " kg" + (J.s(p, "actualWeightKg").isBlank() ? "" : "   Measured: " + J.s(p, "actualWeightKg") + " kg")
                        + "\nEstimated CO2e avoided: " + J.s(p, "estCarbonKg") + " kg (estimate)" + (J.s(p, "passportCode").isBlank() ? "" : "\nAsset: " + J.s(p, "passportCode")), "muted"));
                if (!J.s(p, "lastLat").isBlank()) body.getChildren().add(UI.label("Last recycler position: " + J.s(p, "lastLat") + ", " + J.s(p, "lastLng") + " (" + J.date(p, "lastPingAt") + ")", "muted"));
                if (p.path("certificateNos").size() > 0) { List<String> n = new ArrayList<>(); p.path("certificateNos").forEach(c -> n.add(c.asText())); body.getChildren().add(UI.label("Certificates: " + String.join(", ", n) + " (see Certificates)", "muted")); }
                List<EventTimeline.Row> rows = new ArrayList<>();
                for (JsonNode e : p.path("events")) rows.add(new EventTimeline.Row(J.s(e, "status"), J.date(e, "createdAt") + "  |  " + J.s(e, "actorLabel"), J.s(e, "note")));
                body.getChildren().add(new EventTimeline(rows));
                boolean mineRecycler = Session.userId != null && Session.userId == J.l(p, "recyclerId");
                if (mineRecycler && "EN_ROUTE".equals(J.s(p, "status"))) body.getChildren().add(UI.secondary("Share my location", () -> FormDialog.show("Share location", "Send", List.of(FormDialog.num("lat", "Latitude", true, null), FormDialog.num("lng", "Longitude", true, null)),
                        b -> () -> ApiClient.post("/api/pickups/" + id + "/ping", b), r -> render[0].run())));
                parent.reload();
            });
            Runnable off = LiveClient.listen(ev -> { if ("PICKUP".equals(ev.path("type").asText()) && ev.path("payload").path("id").asLong() == id) render[0].run(); });
            d.setOnHidden(e -> off.run());
            d.getDialogPane().setContent(UI.scroll(body)); d.getDialogPane().setPrefSize(640, 640); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.setResizable(true); d.show(); render[0].run();
        });
    }

    /** Menu helper for recyclers: a verify shortcut is offered through the "Update status" action when the job is at the facility. */
    public static ResourceView jobs() {
        ResourceView v = list(true);
        v.action("Verify & complete", n -> { for (JsonNode a : n.path("nextActions")) if ("VERIFY".equals(a.asText())) return true; return false; }, n -> verifyDialog(n, v));
        return v;
    }
}
