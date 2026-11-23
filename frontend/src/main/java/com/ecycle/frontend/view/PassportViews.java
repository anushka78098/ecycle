package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.component.*;
import com.ecycle.frontend.component.FormDialog.Opt;
import com.ecycle.frontend.navigation.Nav;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Feature A UI: digital passports, QR labels (printed via Swing), genealogy tree, dismantling, component resale/recovery and outcome recording. */
public final class PassportViews {
    private PassportViews() {}

    public static ResourceView mine() {
        ResourceView v = new ResourceView("Digital e-waste passports", "Products you currently own, each with a unique passport, QR code and component genealogy", () -> ApiClient.get("/api/passports/mine"));
        v.col("Passport ID", "code").col("Product", "name").col("Type", "componentType").badge("Stage", "stage").col("Components", n -> String.valueOf(n.path("children").size())).date("Issued", "createdAt")
                .action("Chain of custody", n -> true, n -> ShellController.current.show(CustodyViews.scan(J.s(n, "code")))).action("Open passport", n -> true, n -> open(J.s(n, "code"))).onOpen(n -> open(J.s(n, "code")));
        return v;
    }

    public static void open(String code) { Async.run(() -> ApiClient.get("/api/passports/" + code), PassportViews::dialog); }

    private static void dialog(JsonNode p) {
        Dialog<ButtonType> d = new Dialog<>(); d.setTitle("Passport " + J.s(p, "code")); d.getDialogPane().getStylesheets().add(Nav.css());
        d.getDialogPane().setContent(panel(p, () -> { d.close(); open(J.s(p, "code")); }));
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.getDialogPane().setPrefSize(980, 680); d.setResizable(true); d.show();
    }

    /** Reusable panel (also embedded in the product dialog). @param refresh reloads the passport after a mutating action. */
    public static Node panel(JsonNode p, Runnable refresh) {
        String code = J.s(p, "code");
        JsonNode[] selected = {p};
        ListView<String> events = new ListView<>(); events.setPrefHeight(190);
        Label sel = UI.label("", "section-title");
        Runnable showSel = () -> {
            JsonNode n = selected[0]; sel.setText(J.s(n, "name") + "  (" + J.s(n, "code") + ")");
            List<String> rows = new java.util.ArrayList<>();
            for (JsonNode e : n.path("events")) rows.add(J.date(e, "createdAt") + "  |  " + J.human(J.s(e, "type")) + "  |  " + J.s(e, "actorLabel") + (J.s(e, "detail").isBlank() ? "" : "  |  " + J.s(e, "detail")) + (e.path("valueAmount").isNull() ? "" : "  |  " + J.money(e, "valueAmount")));
            events.setItems(FXCollections.observableArrayList(rows));
        };
        GenealogyTree tree = new GenealogyTree(p, n -> { selected[0] = n; showSel.run(); }); showSel.run();

        HBox qr = new HBox(14, ImageCache.view("/api/passports/" + code + "/qr?size=240", 150, 150),
                new VBox(6, UI.label("Passport ID", "muted"), UI.label(code, "page-title"), UI.badge(J.s(p, "stage")), UI.label("Owner: " + J.s(p, "ownerLabel"), "muted"),
                        UI.secondary("Print QR label (Swing)", () -> printLabel(selected[0])),
                        UI.label("QR payload: " + J.s(p, "qrPayload"), "muted")));
        Button dismantle = UI.secondary("Dismantle into components", () -> dismantle(selected[0], refresh));
        Button outcome = UI.secondary("Record outcome (reuse/repair/refurbish/recycle)", () -> outcome(selected[0], refresh));
        Button comp = UI.secondary("Component action (resell / recover / repair...)", () -> componentAction(selected[0], refresh));
        VBox body = new VBox(12, qr, UI.label("Genealogy tree", "section-title"), tree, new HBox(8, dismantle, outcome, comp), sel, events);
        body.setPadding(new Insets(10)); return UI.scroll(body);
    }

    private static void printLabel(JsonNode n) {
        String code = J.s(n, "code");
        Async.run(() -> ApiClient.bytes("/api/passports/" + code + "/qr?size=400"), bytes -> {
            try {
                BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
                SwingPrint.preview("Passport label", "E-CYCLE DIGITAL PASSPORT", img, code, J.s(n, "name"), "Type: " + J.s(n, "componentType"), "Stage: " + J.human(J.s(n, "stage")), "Scan to open the lifecycle record.");
            } catch (java.io.IOException e) { com.ecycle.frontend.util.Alerts.error(e); }
        });
    }

    private static void dismantle(JsonNode n, Runnable refresh) {
        String preset = "RAM: \nSSD: \nBattery: \nMotherboard: \nDisplay: ";
        FormDialog.show("Dismantle " + J.s(n, "name"), "Create component passports",
                List.of(new FormDialog.F("components", "One component per line as  Type: Name", FormDialog.Type.AREA, true, null, preset)),
                body -> () -> {
                    ArrayNode arr = ApiClient.mapper().createArrayNode();
                    for (String line : body.get("components").asText().split("\\R")) {
                        int i = line.indexOf(':'); if (i < 0) continue; String t = line.substring(0, i).trim(), nm = line.substring(i + 1).trim();
                        if (!t.isEmpty() && !nm.isEmpty()) arr.addObject().put("componentType", t).put("name", nm);
                    }
                    if (arr.isEmpty()) throw new com.ecycle.frontend.api.ApiError(400, "Add at least one component line such as 'RAM: 8GB DDR4' (fill in the name after the colon).", null);
                    return ApiClient.post("/api/passports/" + J.s(n, "code") + "/dismantle", J.obj().set("components", arr));
                }, r -> refresh.run());
    }
    private static void outcome(JsonNode n, Runnable refresh) {
        FormDialog.show("Record outcome", "Record", List.of(FormDialog.combo("pathway", "Outcome", true, FormDialog.enumOpts(com.ecycle.shared.Enums.Pathway.class, false), "REUSE"),
                FormDialog.area("note", "Note", false), FormDialog.num("measuredWeightKg", "Measured unit weight in kg (verified recyclers/admins only)", false, null)),
                b -> () -> ApiClient.post("/api/passports/" + J.s(n, "code") + "/outcome", b), r -> refresh.run());
    }
    private static void componentAction(JsonNode n, Runnable refresh) {
        FormDialog.show("Component action: " + J.s(n, "name"), "Apply", List.of(
                FormDialog.combo("action", "Action", true, List.of(new Opt("RESELL", "Resell to a buyer"), new Opt("RECOVER", "Recover / recycle"), new Opt("REUSE", "Reuse"), new Opt("REPAIR", "Repair"), new Opt("REFURBISH", "Refurbish")), "RESELL"),
                FormDialog.integer("toUserId", "Buyer user ID (for resale)", false, null), FormDialog.num("amount", "Value / sale price", false, null), FormDialog.area("note", "Note", false)),
                b -> () -> ApiClient.post("/api/passports/" + J.s(n, "code") + "/component-action", b), r -> refresh.run());
    }
}
