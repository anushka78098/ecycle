package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.component.*;
import com.ecycle.frontend.component.FormDialog.F;
import com.ecycle.frontend.component.FormDialog.Opt;
import com.ecycle.frontend.navigation.Nav;
import com.ecycle.frontend.util.Alerts;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.ecycle.shared.Enums;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.util.*;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** Seller workspace: listings (create/edit/withdraw/images), offers, and hazard + data-sanitization workflow. */
public final class SellerViews {
    private SellerViews() {}

    public static ResourceView listings() {
        ResourceView v = new ResourceView("My listings", "Create products and waste listings, track approval, upload photos", () -> ApiClient.get("/api/listings/mine"));
        v.col("#", "id").col("Product", "name").col("Category", "categoryName").money("Price", "expectedPrice").badge("Status", "status").col("Score", n -> String.format("%.0f", J.d(n, "score.overall")))
                .badge("Pathway", "pathway").badge("Hazard", "hazardLevel").col("Reason", "rejectionReason")
                .tool("New listing", () -> form(null, v))
                .action("Edit", n -> Set.of("PENDING_APPROVAL", "REJECTED").contains(J.s(n, "status")), n -> form(n, v))
                .action("Upload photos", n -> !Set.of("SOLD", "WITHDRAWN").contains(J.s(n, "status")), n -> photos(n, v))
                .action("Withdraw", "btn-danger", n -> Set.of("PENDING_APPROVAL", "REJECTED").contains(J.s(n, "status")), n -> Alerts.confirm("Withdraw this listing? History is kept.", () -> Async.run(() -> ApiClient.delete("/api/listings/" + J.l(n, "id")), r -> v.reload())))
                .action("Details", n -> true, n -> new ProductDialog(J.l(n, "id")).show());
        return v;
    }

    private static void photos(JsonNode n, ResourceView v) {
        FileChooser fc = new FileChooser(); fc.setTitle("Select product photos (JPG, PNG, WEBP, max 5 MB, up to 8)");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.webp"));
        List<File> files = fc.showOpenMultipleDialog(Nav.stage()); if (files == null || files.isEmpty()) return;
        Async.run(() -> ApiClient.upload("/api/listings/" + J.l(n, "id") + "/images", Map.of(), "files", files), r -> { Alerts.info(files.size() + " photo(s) uploaded."); v.reload(); });
    }

    /** Create or edit. The hazard questionnaire is built from the admin-configurable rules in the database. */
    private static void form(JsonNode ex, ResourceView v) {
        Async.run(() -> { JsonNode cats = ApiClient.get("/api/categories"); JsonNode rules = ApiClient.get("/api/hazard/rules"); return new JsonNode[]{cats, rules}; }, data -> {
            List<Opt> cats = new ArrayList<>(); data[0].forEach(c -> cats.add(new Opt(c.path("id").asText(), c.path("name").asText())));
            boolean e = ex != null; String ini = e ? J.s(ex, "categoryId") : null;
            List<F> fields = List.of(FormDialog.text("name", "Product name", true, e ? J.s(ex, "name") : null), FormDialog.text("brand", "Brand", false, e ? J.s(ex, "brand") : null), FormDialog.text("model", "Model", false, e ? J.s(ex, "model") : null),
                    FormDialog.combo("categoryId", "Category", true, cats, ini),
                    FormDialog.integer("ageMonths", "Product age (months)", true, e ? J.s(ex, "ageMonths") : "12"),
                    FormDialog.combo("physicalCondition", "Physical condition", true, FormDialog.enumOpts(Enums.ItemCondition.class, false), e ? J.s(ex, "physicalCondition") : "GOOD"),
                    FormDialog.combo("functionalStatus", "Functional status", true, FormDialog.enumOpts(Enums.FunctionalStatus.class, false), e ? J.s(ex, "functionalStatus") : "FULLY_WORKING"),
                    FormDialog.integer("quantity", "Quantity (units in this lot)", true, e ? J.s(ex, "quantity") : "1"), FormDialog.num("expectedPrice", "Expected price for the whole lot (INR)", true, e ? J.s(ex, "expectedPrice") : null),
                    FormDialog.num("weightKg", "Weight per unit (kg)", true, e ? J.s(ex, "weightKg") : "1"),
                    FormDialog.combo("pathway", "Preferred pathway (blank = use recommendation)", false, FormDialog.enumOpts(Enums.Pathway.class, true), e ? J.s(ex, "pathway") : ""),
                    FormDialog.text("pickupCity", "Pickup city", true, e ? J.s(ex, "pickupCity") : null), FormDialog.text("pickupAddress", "Pickup address", false, e ? J.s(ex, "pickupAddress") : null),
                    FormDialog.area("description", "Description", false), FormDialog.check("auctionEnabled", "Sell via reverse auction (bidders compete on price and recovery capability)", e && J.b(ex, "auctionEnabled")),
                    FormDialog.integer("auctionHours", "Auction duration in hours (1-720)", false, "72"), FormDialog.num("auctionMinPrice", "Auction minimum price (INR)", false, null));
            VBox box = new VBox(6); box.setPadding(new Insets(6));
            box.getChildren().add(UI.label("Inspection questionnaire (tick everything that applies). This is a self-reported checklist, not a hardware diagnostic.", "section-title"));
            Map<String, CheckBox> checks = new LinkedHashMap<>();
            for (JsonNode r : data[1]) { if (!r.path("enabled").asBoolean()) continue; CheckBox cb = new CheckBox(J.s(r, "description")); cb.setWrapText(true); checks.put(J.s(r, "code"), cb); box.getChildren().add(cb); }
            FormDialog.show(e ? "Edit listing" : "New listing", e ? "Resubmit for approval" : "Submit for approval", fields, box, body -> () -> {
                ObjectNode answers = J.obj(); checks.forEach((k, cb) -> { if (cb.isSelected()) answers.put(k, true); }); body.set("hazardAnswers", answers);
                if (!body.has("quantity")) body.put("quantity", 1);
                body.put("categoryId", body.get("categoryId").asLong()); body.put("auctionEnabled", body.path("auctionEnabled").asBoolean(false));
                return e ? ApiClient.put("/api/listings/" + J.l(ex, "id"), body) : ApiClient.post("/api/listings", body);
            }, res -> { Alerts.info("Saved. Circular Value Score " + String.format("%.1f", J.d(res, "score.overall")) + ", recommended pathway " + J.human(J.s(res, "score.recommendedPathway"))
                    + ", hazard level " + J.human(J.s(res, "hazardLevel")) + ". Awaiting admin approval."); v.reload(); });
        });
    }

    public static ResourceView offers() {
        ResourceView v = new ResourceView("Offers received", "Accept an offer to create an order for that buyer", () -> ApiClient.get("/api/offers/received"));
        v.col("Offer", "id").col("Product", "listingName").col("Buyer", "buyerName").money("Asking", "askingPrice").money("Offered", "amount").col("Message", "message").badge("Status", "status").date("When", "createdAt")
                .action("Accept", "btn-primary", n -> "PENDING".equals(J.s(n, "status")), n -> Alerts.confirm("Accept " + J.money(n, "amount") + " from " + J.s(n, "buyerName") + "? Other offers will be declined.",
                        () -> Async.run(() -> ApiClient.post("/api/offers/" + J.l(n, "id") + "/accept", null), r -> { Alerts.info("Order #" + r.path("id").asLong() + " created. The buyer was notified to pay."); v.reload(); })))
                .action("Reject", "btn-danger", n -> "PENDING".equals(J.s(n, "status")), n -> Async.run(() -> ApiClient.post("/api/offers/" + J.l(n, "id") + "/reject", null), r -> v.reload()));
        return v;
    }

    /** Hazard inspection history, re-assessment and data-sanitization evidence for the seller's own products. */
    public static ResourceView hazard() {
        ResourceView v = new ResourceView("Hazard & data safety", "Re-assess a product, and submit data-erasure evidence for devices with storage", () -> ApiClient.get("/api/listings/mine"));
        v.col("#", "id").col("Product", "name").badge("Hazard", "hazardLevel").yesNo("Shipping blocked", "shippingBlocked").col("Specialist", "specialistRequired").yesNo("Needs sanitization", "requiresSanitization").yesNo("Sanitization verified", "sanitizationVerified")
                .action("Re-assess hazards", n -> !Set.of("SOLD", "RESERVED").contains(J.s(n, "status")), n -> reassess(n, v))
                .action("Submit sanitization evidence", n -> J.b(n, "requiresSanitization") && !J.b(n, "sanitizationVerified"), n -> FormDialog.show("Data sanitization evidence", "Submit for verification", List.of(
                        FormDialog.text("method", "Method (e.g. NIST 800-88 Purge, crypto-erase, physical destruction)", true), FormDialog.text("toolName", "Tool used", false), FormDialog.text("evidenceRef", "Report ID / file hash / log reference", true)),
                        b -> () -> ApiClient.post("/api/listings/" + J.l(n, "id") + "/sanitization", b), r -> { Alerts.info("Submitted. An admin must verify the evidence before a certificate is issued."); v.reload(); }))
                .action("Data-wipe checklist", n -> J.b(n, "requiresSanitization") && !J.b(n, "sanitizationVerified"), n -> WipeDialog.open(J.l(n, "id"), v::reload))
                .action("View certificate", n -> J.b(n, "sanitizationVerified"), n -> Async.run(() -> ApiClient.get("/api/listings/" + J.l(n, "id") + "/sanitization/certificate"), c ->
                        SwingPrint.preview("Data sanitization certificate", "DATA SANITIZATION CERTIFICATE", null, "Certificate: " + J.s(c, "certificateNo"), "Product: " + J.s(c, "listingName"), "Method: " + J.s(c, "method"), "Tool: " + J.s(c, "toolName"),
                                "Evidence ref: " + J.s(c, "evidenceRef"), "Verified by: " + J.s(c, "verifiedByName"), "Verified at: " + J.date(c, "verifiedAt"), "Issued only after administrator verification of the evidence.")));
        return v;
    }
    private static void reassess(JsonNode n, ResourceView v) {
        Async.run(() -> ApiClient.get("/api/hazard/rules"), rules -> {
            VBox box = new VBox(6); Map<String, CheckBox> checks = new LinkedHashMap<>();
            for (JsonNode r : rules) { if (!r.path("enabled").asBoolean()) continue; CheckBox cb = new CheckBox(J.s(r, "description")); cb.setWrapText(true); checks.put(J.s(r, "code"), cb); box.getChildren().add(cb); }
            FormDialog.show("Re-assess " + J.s(n, "name"), "Save assessment", List.of(), box, b -> () -> { ObjectNode a = J.obj(); checks.forEach((k, cb) -> a.put(k, cb.isSelected())); ObjectNode body = J.obj(); body.set("answers", a);
                return ApiClient.post("/api/listings/" + J.l(n, "id") + "/hazard", body); },
                r -> { Alerts.info("Hazard level: " + J.human(J.s(r, "level")) + "\n" + String.join("\n", r.path("findings").findValuesAsText("")) ); v.reload(); });
        });
    }
}
