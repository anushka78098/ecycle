package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.api.Q;
import com.ecycle.frontend.api.Session;
import com.ecycle.frontend.component.*;
import com.ecycle.frontend.component.FormDialog.Opt;
import com.ecycle.frontend.navigation.Nav;
import com.ecycle.frontend.util.Alerts;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.ecycle.shared.Enums;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

/** Dispute queue, dispute detail (evidence viewer, investigation timeline, messages) and the resolver workspace. */
public final class DisputeViews {
    private DisputeViews() {}
    private static final Set<String> FINAL = Set.of("RESOLVED", "REJECTED");

    private static ResourceView table(String title, String sub, java.util.concurrent.Callable<JsonNode> loader, boolean staff) {
        ResourceView v = new ResourceView(title, sub, loader);
        v.col("#", "id").badge("Priority", "priority").badge("Status", "status").col("Category", n -> J.human(J.s(n, "category"))).col("Product", "listingName").col("Raised by", "raisedByName").col("Against", "againstName")
                .col("Resolver", "resolverName").money("Order value", "orderAmount").date("Opened", "createdAt")
                .action("Open case", n -> true, n -> open(J.l(n, "id"), staff, v::reload)).onOpen(n -> open(J.l(n, "id"), staff, v::reload));
        return v;
    }
    public static ResourceView mine() { return table("My disputes", "Cases you raised or that were raised against you. Upload evidence and talk to the resolver here.", () -> ApiClient.get("/api/disputes/mine"), false); }
    public static ResourceView history() { return table("Resolution history", "Cases you resolved or rejected", () -> ApiClient.get("/api/resolver/history"), true); }

    /** Resolver / admin queue with status and priority filters and one-click claim. */
    public static Node centre(boolean admin) {
        ComboBox<Opt> st = new ComboBox<>(), pr = new ComboBox<>();
        st.getItems().add(new Opt("", "All statuses")); st.getItems().addAll(FormDialog.enumOpts(Enums.DisputeStatus.class, false)); st.setValue(st.getItems().get(0));
        pr.getItems().add(new Opt("", "All priorities")); pr.getItems().addAll(FormDialog.enumOpts(Enums.DisputePriority.class, false)); pr.setValue(pr.getItems().get(0));
        ResourceView[] ref = new ResourceView[1];
        ref[0] = table(admin ? "Dispute monitoring" : "Dispute centre", "Sorted by urgency: high priority and open cases first", () -> ApiClient.get("/api/resolver/disputes" + Q.of("status", st.getValue() == null ? null : st.getValue().value(), "priority", pr.getValue() == null ? null : pr.getValue().value())), true);
        ResourceView v = ref[0];
        st.setOnAction(e -> v.reload()); pr.setOnAction(e -> v.reload());
        v.node(st).node(pr);
        v.action("Assign to me", "btn-primary", n -> !FINAL.contains(J.s(n, "status")) && (J.s(n, "resolverId").isBlank() || admin), n -> Async.run(() -> ApiClient.post("/api/resolver/disputes/" + J.l(n, "id") + "/assign", J.obj()), r -> v.reload()));
        if (admin) v.action("Assign to resolver...", n -> !FINAL.contains(J.s(n, "status")), n -> Async.run(() -> ApiClient.get("/api/admin/resolvers"), rs -> {
            List<Opt> o = new java.util.ArrayList<>(); rs.forEach(x -> o.add(new Opt(x.path("id").asText(), x.path("fullName").asText() + " <" + x.path("email").asText() + ">")));
            FormDialog.show("Assign dispute #" + J.l(n, "id"), "Assign", List.of(FormDialog.combo("resolverId", "Resolver", true, o, null)), b -> () -> { b.put("resolverId", b.get("resolverId").asLong()); return ApiClient.post("/api/resolver/disputes/" + J.l(n, "id") + "/assign", b); }, r -> v.reload()); }));
        return v;
    }

    public static void open(long id, boolean staff, Runnable onChange) {
        Dialog<ButtonType> d = new Dialog<>(); d.setTitle("Dispute #" + id); d.getDialogPane().getStylesheets().add(Nav.css());
        VBox root = new VBox(); Runnable[] reload = new Runnable[1];
        reload[0] = () -> Async.run(() -> ApiClient.get("/api/disputes/" + id), det -> { root.getChildren().setAll(detail(det, staff, reload[0])); onChange.run(); });
        reload[0].run();
        d.getDialogPane().setContent(root); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.getDialogPane().setPrefSize(1060, 720); d.setResizable(true); d.show();
    }

    private static Node detail(JsonNode det, boolean staff, Runnable reload) {
        JsonNode dsp = det.path("dispute"); long id = J.l(dsp, "id"); boolean fin = FINAL.contains(J.s(dsp, "status"));
        boolean admin = Session.is("RESOLVER"), handler = staff && (admin || (Session.userId != null && Session.userId == J.l(dsp, "resolverId")));
        VBox info = UI.card(UI.row(UI.label("Dispute #" + id, "page-title"), UI.badge(J.s(dsp, "status")), UI.badge(J.s(dsp, "priority")), UI.label(J.human(J.s(dsp, "category")), "section-title")),
                UI.label(J.s(dsp, "description"), "label"),
                UI.label("Buyer: " + J.s(det, "buyer.displayName") + (J.s(det, "buyer.city").isBlank() ? "" : " (" + J.s(det, "buyer.city") + ")") + (J.s(det, "buyer.contact").isBlank() ? "" : " | " + J.s(det, "buyer.contact"))
                        + "     Seller: " + J.s(det, "seller.displayName") + (J.s(det, "seller.city").isBlank() ? "" : " (" + J.s(det, "seller.city") + ")") + (J.s(det, "seller.contact").isBlank() ? "" : " | " + J.s(det, "seller.contact")), "muted"),
                UI.label("Order #" + J.l(det, "order.orderId") + " - " + J.s(det, "order.listingName") + " | " + J.money(det, "order.amount") + " | order " + J.human(J.s(det, "order.orderStatus")) + " | payment " + J.human(J.s(det, "order.paymentStatus")) + " | shipment " + J.human(J.s(det, "order.shipmentStatus")), "muted"),
                UI.label("Resolver: " + (J.s(dsp, "resolverName").isBlank() ? "unassigned" : J.s(dsp, "resolverName")), "muted"));
        if (staff) info.getChildren().add(UI.label("Privacy: resolvers see names and city only. Emails, phones and addresses are not exposed, and no payment records can be edited here.", "muted"));
        if (!det.path("resolution").isNull() && !det.path("resolution").isMissingNode())
            info.getChildren().add(UI.label("DECISION: " + J.human(J.s(det, "resolution.type")) + (J.d(det, "resolution.refundAmount") > 0 ? " | refund " + J.money(det, "resolution.refundAmount") : "") + " | " + J.s(det, "resolution.decisionText") + " (by " + J.s(det, "resolution.resolverName") + ")", "ok-text"));

        ListView<JsonNode> ev = new ListView<>(FXCollections.observableArrayList(iter(det.path("evidence")))); ev.setPrefHeight(150);
        ev.setCellFactory(l -> new ListCell<>() { @Override protected void updateItem(JsonNode n, boolean e) { super.updateItem(n, e); setText(e || n == null ? null :
                J.date(n, "createdAt") + " | " + J.s(n, "submitterRole") + " " + J.s(n, "submitterName") + " | " + J.human(J.s(n, "category")) + " | " + J.s(n, "originalName") + " | sha256 " + J.s(n, "sha256").substring(0, 10) + "..." + (J.s(n, "description").isBlank() ? "" : " | " + J.s(n, "description"))); } });
        StackPane preview = new StackPane(UI.label("Select image evidence to preview.", "muted"));
        preview.setPrefSize(280, 180); preview.setMinHeight(150); preview.getStyleClass().add("photo-ph");
        ev.getSelectionModel().selectedItemProperty().addListener((o, a, selected) -> showEvidencePreview(id, selected, preview));
        if (!ev.getItems().isEmpty()) ev.getSelectionModel().selectFirst();
        Button view = UI.secondary("Open selected evidence", () -> { JsonNode s = ev.getSelectionModel().getSelectedItem(); if (s != null) openEvidence(id, s); });
        HBox evBar = new HBox(8, view);
        if (!staff && !fin) evBar.getChildren().add(UI.primary("Upload evidence", () -> upload(id, reload)));
        VBox evidence = UI.card(UI.label("Evidence (immutable once uploaded, fingerprinted with SHA-256)", "section-title"), ev, preview, evBar);

        ListView<JsonNode> msgs = new ListView<>(FXCollections.observableArrayList(iter(det.path("messages")))); msgs.setPrefHeight(230);
        msgs.setCellFactory(l -> new ListCell<>() { @Override protected void updateItem(JsonNode n, boolean e) { super.updateItem(n, e); if (e || n == null) { setText(null); setGraphic(null); return; }
            String vis = J.s(n, "visibility"); setText(J.date(n, "createdAt") + " | " + J.s(n, "senderName") + ("PARTIES".equals(vis) ? "" : " [" + J.human(vis) + "]") + "\n" + J.s(n, "body"));
            setStyle("INTERNAL".equals(vis) ? "-fx-background-color:#fef9c3;" : "EVIDENCE_REQUEST".equals(vis) ? "-fx-background-color:#e0f2fe;" : ""); setWrapText(true); } });
        HBox msgBar = new HBox(8);
        if (!fin) {
            msgBar.getChildren().add(UI.primary("Send message", () -> FormDialog.show("Message", "Send", List.of(FormDialog.area("body", "Message", true)), b -> () -> { b.put("visibility", "PARTIES"); return ApiClient.post("/api/disputes/" + id + "/messages", b); }, r -> reload.run())));
            if (handler) {
                msgBar.getChildren().add(UI.secondary("Request evidence", () -> FormDialog.show("Request additional evidence", "Send request", List.of(FormDialog.area("body", "What do you need, and from whom?", true)), b -> () -> { b.put("visibility", "EVIDENCE_REQUEST"); return ApiClient.post("/api/disputes/" + id + "/messages", b); }, r -> reload.run())));
                msgBar.getChildren().add(UI.secondary("Add investigation note (internal)", () -> FormDialog.show("Investigation log entry", "Save", List.of(FormDialog.area("body", "Internal note (parties cannot see this)", true)), b -> () -> { b.put("visibility", "INTERNAL"); return ApiClient.post("/api/disputes/" + id + "/messages", b); }, r -> reload.run())));
            }
        }
        VBox conv = UI.card(UI.label(staff ? "Investigation timeline and communication" : "Messages", "section-title"), msgs, msgBar);
        if (!det.path("assignmentHistory").isEmpty()) { VBox h = new VBox(2); det.path("assignmentHistory").forEach(a -> h.getChildren().add(UI.label(a.asText(), "muted"))); conv.getChildren().add(h); }

        VBox body = new VBox(12, info, new HBox(12, grow(evidence), grow(conv)));
        if (staff && !fin) {
            HBox act = new HBox(8);
            if (J.s(dsp, "resolverId").isBlank() || admin) act.getChildren().add(UI.secondary("Assign to me", () -> Async.run(() -> ApiClient.post("/api/resolver/disputes/" + id + "/assign", J.obj()), r -> reload.run())));
            if (handler) {
                act.getChildren().add(UI.secondary("Set priority", () -> FormDialog.show("Priority", "Set", List.of(FormDialog.combo("priority", "Priority", true, FormDialog.enumOpts(Enums.DisputePriority.class, false), J.s(dsp, "priority"))), b -> () -> ApiClient.put("/api/resolver/disputes/" + id + "/priority", b), r -> reload.run())));
                act.getChildren().add(UI.primary("Resolve with decision", () -> FormDialog.show("Issue resolution", "Issue decision", List.of(
                        FormDialog.combo("type", "Resolution", true, FormDialog.enumOpts(Enums.ResolutionType.class, false), "FULL_REFUND"), FormDialog.num("refundAmount", "Refund amount (needed for partial refund; max = paid amount)", false, null),
                        FormDialog.area("decisionText", "Decision and reasoning (sent to both parties)", true)), b -> () -> ApiClient.post("/api/resolver/disputes/" + id + "/resolve", b),
                        r -> { Alerts.info("Decision recorded. Any refund was executed by the payment system, not by the resolver."); reload.run(); })));
                act.getChildren().add(UI.danger("Reject dispute", () -> FormDialog.show("Reject dispute", "Reject", List.of(FormDialog.area("reason", "Reason", true)), b -> () -> ApiClient.post("/api/resolver/disputes/" + id + "/reject", b), r -> reload.run())));
            }
            body.getChildren().add(UI.card(UI.label("Resolver actions", "section-title"), act));
        }
        body.setPadding(new Insets(8)); return UI.scroll(body);
    }
    private static Node grow(Node n) { HBox.setHgrow(n, Priority.ALWAYS); ((Region) n).setPrefWidth(480); return n; }
    private static List<JsonNode> iter(JsonNode arr) { List<JsonNode> l = new java.util.ArrayList<>(); arr.forEach(l::add); return l; }

    private static void showEvidencePreview(long disputeId, JsonNode evidence, StackPane preview) {
        if (evidence == null) {
            preview.getChildren().setAll(UI.label("Select image evidence to preview.", "muted"));
        } else if (!J.s(evidence, "contentType").startsWith("image/")) {
            preview.getChildren().setAll(UI.label("Inline preview is available for images. Open this file to view it.", "muted"));
        } else {
            preview.getChildren().setAll(ImageCache.view("/api/disputes/" + disputeId + "/evidence/" + J.l(evidence, "id") + "/file", 260, 170));
        }
    }

    private static void upload(long id, Runnable reload) {
        FileChooser fc = new FileChooser(); fc.setTitle("Select evidence (JPG, PNG, WEBP or PDF, max 10 MB)");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Evidence", "*.jpg", "*.jpeg", "*.png", "*.webp", "*.pdf"));
        File f = fc.showOpenDialog(Nav.stage()); if (f == null) return;
        FormDialog.show("Evidence details", "Upload", List.of(FormDialog.combo("category", "Category", true, FormDialog.enumOpts(Enums.EvidenceCategory.class, false), "PHOTO"), FormDialog.text("description", "What does this show?", false)),
                b -> () -> ApiClient.upload("/api/disputes/" + id + "/evidence", java.util.Map.of("category", b.get("category").asText(), "description", b.path("description").asText("")), "file", List.of(f)), r -> reload.run());
    }
    private static void openEvidence(long id, JsonNode ev) {
        Async.run(() -> { byte[] b = ApiClient.bytes("/api/disputes/" + id + "/evidence/" + J.l(ev, "id") + "/file"); String ext = J.s(ev, "contentType").contains("pdf") ? ".pdf" : J.s(ev, "contentType").contains("png") ? ".png" : J.s(ev, "contentType").contains("webp") ? ".webp" : ".jpg";
            File tmp = File.createTempFile("ecycle-evidence-", ext); tmp.deleteOnExit(); Files.write(tmp.toPath(), b); return tmp; },
                tmp -> { if (java.awt.Desktop.isDesktopSupported()) { Thread t = new Thread(() -> { try { java.awt.Desktop.getDesktop().open(tmp); } catch (java.io.IOException e) { javafx.application.Platform.runLater(() -> Alerts.error(e)); } }); t.setDaemon(true); t.start(); } else Alerts.info("Saved to " + tmp); });
    }
}
