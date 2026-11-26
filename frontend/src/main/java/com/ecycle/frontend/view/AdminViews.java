package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.api.Q;
import com.ecycle.frontend.component.*;
import com.ecycle.frontend.component.FormDialog.Opt;
import com.ecycle.frontend.util.Alerts;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.ecycle.shared.Enums;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.List;
import javafx.scene.control.TextField;

/** Administration: users, verification, moderation, catalogue, safety rules, scoring/impact configuration, transactions and audit trail. */
public final class AdminViews {
    private AdminViews() {}

    public static ResourceView users() {
        TextField search = new TextField(); search.setPromptText("Search name or email");
        ResourceView[] ref = new ResourceView[1];
        ref[0] = new ResourceView("User management", "Suspend or reactivate accounts, create resolver and admin staff", () -> ApiClient.get("/api/admin/users" + Q.of("q", search.getText())));
        ResourceView v = ref[0]; search.setOnAction(e -> v.reload());
        v.node(search).col("#", "id").col("Name", "fullName").col("Email", "email").col("Roles", n -> { StringBuilder sb = new StringBuilder(); n.path("roles").forEach(r -> sb.append(J.human(r.asText())).append(' ')); return sb.toString().trim(); })
                .badge("Account", "status").badge("Seller verification", "verificationStatus").date("Joined", "createdAt")
                .tool("New staff account", () -> FormDialog.show("Create staff account", "Create", List.of(FormDialog.text("fullName", "Full name", true), FormDialog.text("email", "Email", true), FormDialog.pass("password", "Temporary password"),
                        FormDialog.combo("role", "Role", true, FormDialog.opts("RESOLVER"), "RESOLVER")), b -> () -> ApiClient.post("/api/admin/staff", b), r -> v.reload()))
                .action("Suspend", "btn-danger", n -> "ACTIVE".equals(J.s(n, "status")), n -> Alerts.confirm("Suspend " + J.s(n, "email") + "? They are signed out immediately.", () -> Async.run(() -> ApiClient.put("/api/admin/users/" + J.l(n, "id") + "/status?active=false", null), r -> v.reload())))
                .action("Reactivate", n -> "SUSPENDED".equals(J.s(n, "status")), n -> Async.run(() -> ApiClient.put("/api/admin/users/" + J.l(n, "id") + "/status?active=true", null), r -> v.reload()));
        return v;
    }

    public static ResourceView verification() {
        ResourceView[] ref = new ResourceView[1];
        ref[0] = new ResourceView("Seller & buyer verification", "Sellers need verification before listings can be approved. Verified buyers may bid in auctions; their recovery capability and environmental rating feed the Circular Bid Score.", () -> {
            ArrayNode out = ApiClient.mapper().createArrayNode();
            for (JsonNode u : ApiClient.get("/api/admin/users")) { boolean seller = hasRole(u, "SELLER") && !"VERIFIED".equals(J.s(u, "verificationStatus")); boolean buyer = hasRole(u, "BUYER") && !J.b(u, "buyerVerified"); if (seller || buyer) out.add(u); }
            return out; });
        ResourceView v = ref[0];
        v.col("#", "id").col("Name", "fullName").col("Email", "email").col("Role", n -> hasRole(n, "SELLER") ? "Seller" : "Buyer").badge("Seller status", "verificationStatus").col("Buyer type", n -> J.human(J.s(n, "buyerType")))
                .action("Verify seller", "btn-primary", n -> hasRole(n, "SELLER"), n -> Async.run(() -> ApiClient.post("/api/admin/sellers/" + J.l(n, "id") + "/verify?approve=true", null), r -> v.reload()))
                .action("Reject seller", "btn-danger", n -> hasRole(n, "SELLER"), n -> Async.run(() -> ApiClient.post("/api/admin/sellers/" + J.l(n, "id") + "/verify?approve=false", null), r -> v.reload()))
                .action("Verify buyer", "btn-primary", n -> hasRole(n, "BUYER"), n -> FormDialog.show("Verify buyer " + J.s(n, "fullName"), "Verify", List.of(
                        FormDialog.integer("recoveryCapability", "Verified recovery capability (0-100)", true, "60"), FormDialog.integer("environmentalRating", "Verified environmental performance (0-100)", true, "60")),
                        b -> () -> { b.put("approve", true); return ApiClient.post("/api/admin/buyers/" + J.l(n, "id") + "/verify", b); }, r -> v.reload()));
        return v;
    }
    private static boolean hasRole(JsonNode u, String r) { for (JsonNode x : u.path("roles")) if (r.equals(x.asText())) return true; return false; }

    private static ResourceView listingsTable(String title, String sub, String path) {
        ResourceView v = new ResourceView(title, sub, () -> ApiClient.get(path));
        v.col("#", "id").col("Product", "name").col("Seller", n -> J.s(n, "sellerName") + (J.b(n, "sellerVerified") ? "" : " (unverified)")).col("Category", "categoryName").money("Price", "expectedPrice")
                .col("Score", n -> String.format("%.0f", J.d(n, "score.overall"))).badge("Hazard", "hazardLevel").badge("Status", "status").date("Submitted", "createdAt")
                .action("Details", n -> true, n -> new ProductDialog(J.l(n, "id")).show())
                .action("Approve", "btn-primary", n -> "PENDING_APPROVAL".equals(J.s(n, "status")), n -> Async.run(() -> ApiClient.post("/api/admin/listings/" + J.l(n, "id") + "/approve", null), r -> v.reload()))
                .action("Reject / take down", "btn-danger", n -> Set_.in(J.s(n, "status"), "PENDING_APPROVAL", "APPROVED"), n -> FormDialog.show("Reject listing", "Reject", List.of(FormDialog.text("reason", "Reason shown to the seller", true)),
                        b -> () -> ApiClient.post("/api/admin/listings/" + J.l(n, "id") + "/reject", b), r -> v.reload()));
        return v;
    }
    private static final class Set_ { static boolean in(String s, String... o) { for (String x : o) if (x.equals(s)) return true; return false; } }
    public static ResourceView pendingListings() { return listingsTable("Listing approval", "Review the score, hazard level and seller verification before approving", "/api/admin/listings/pending"); }
    public static ResourceView allListings() { return listingsTable("Listing moderation", "All listings. Use take-down to remove inappropriate or unsafe items.", "/api/admin/listings"); }

    public static ResourceView categories() {
        ResourceView v = new ResourceView("Product categories", "Baselines used by the Circular Value Score (repairability, component recovery, expected life)", () -> ApiClient.get("/api/admin/categories"));
        v.col("#", "id").col("Name", "name").col("Repairability", "repairabilityBase").col("Component recovery", "componentRecoveryBase").col("Life (months)", "lifeMonths").col("Default kg", "defaultWeightKg")
                .yesNo("Dismantlable", "dismantlable").yesNo("Active", "active").tool("New category", () -> catForm(null, v)).action("Edit", n -> true, n -> catForm(n, v));
        return v;
    }
    private static void catForm(JsonNode n, ResourceView v) {
        boolean e = n != null;
        FormDialog.show(e ? "Edit category" : "New category", "Save", List.of(FormDialog.text("name", "Name", true, e ? J.s(n, "name") : null), FormDialog.text("description", "Description", false, e ? J.s(n, "description") : null),
                FormDialog.integer("repairabilityBase", "Repairability baseline (0-100)", true, e ? J.s(n, "repairabilityBase") : "50"), FormDialog.integer("componentRecoveryBase", "Component recovery baseline (0-100)", true, e ? J.s(n, "componentRecoveryBase") : "40"),
                FormDialog.integer("lifeMonths", "Expected life (months)", true, e ? J.s(n, "lifeMonths") : "60"), FormDialog.num("defaultWeightKg", "Typical weight (kg)", true, e ? J.s(n, "defaultWeightKg") : "1"),
                FormDialog.check("dismantlable", "Can be dismantled into tracked components", e && J.b(n, "dismantlable")), FormDialog.check("active", "Active", !e || J.b(n, "active"))),
                b -> () -> e ? ApiClient.put("/api/admin/categories/" + J.l(n, "id"), b) : ApiClient.post("/api/admin/categories", b), r -> v.reload());
    }

    public static ResourceView hazardRules() {
        ResourceView v = new ResourceView("Hazard risk rules", "Points are summed into a risk level (<15 low, <35 moderate, <60 high, else critical). Blocking rules stop normal shipping.", () -> ApiClient.get("/api/admin/hazard/rules"));
        v.col("Code", "code").col("Question shown to sellers", "description").col("Points", "points").yesNo("Blocks shipping", "blocksShipping").yesNo("Needs sanitization", "requiresSanitization").col("Specialist", "specialistType").yesNo("Enabled", "enabled")
                .tool("New rule", () -> ruleForm(null, v)).action("Edit", n -> true, n -> ruleForm(n, v));
        return v;
    }
    private static void ruleForm(JsonNode n, ResourceView v) {
        boolean e = n != null;
        FormDialog.show(e ? "Edit rule " + J.s(n, "code") : "New hazard rule", "Save", List.of(FormDialog.text("code", e ? "Code (fixed)" : "Code (e.g. SHARP_EDGES)", true, e ? J.s(n, "code") : null), FormDialog.text("description", "Question / description", true, e ? J.s(n, "description") : null),
                FormDialog.integer("points", "Risk points (0-100)", true, e ? J.s(n, "points") : "10"), FormDialog.check("blocksShipping", "Blocks normal shipping", e && J.b(n, "blocksShipping")),
                FormDialog.check("requiresSanitization", "Requires data sanitization", e && J.b(n, "requiresSanitization")), FormDialog.area("handling", "Safe handling procedure", true), FormDialog.text("specialistType", "Specialist handler type", false, e ? J.s(n, "specialistType") : null),
                FormDialog.check("enabled", "Enabled", !e || J.b(n, "enabled"))),
                b -> () -> e ? ApiClient.put("/api/admin/hazard/rules/" + J.l(n, "id"), b) : ApiClient.post("/api/admin/hazard/rules", b), r -> v.reload());
    }

    public static ResourceView sanitization() {
        ResourceView v = new ResourceView("Sanitization verification", "Review submitted erase evidence. A certificate is issued only when you approve. This is your attestation; the platform cannot read the device.", () -> ApiClient.get("/api/admin/sanitization/pending"));
        v.col("#", "id").col("Product", "listingName").col("Method", "method").col("Tool", "toolName").col("Evidence reference", "evidenceRef").col("Submitted by", "submittedByName").date("When", "createdAt")
                .action("Approve & issue certificate", "btn-primary", n -> true, n -> FormDialog.show("Approve sanitization", "Approve", List.of(FormDialog.text("note", "Verification note", false)),
                        b -> () -> { b.put("approve", true); return ApiClient.post("/api/admin/sanitization/" + J.l(n, "id") + "/verify", b); }, r -> { Alerts.info("Certificate " + J.s(r, "certificateNo") + " issued."); v.reload(); }))
                .action("Reject", "btn-danger", n -> true, n -> FormDialog.show("Reject evidence", "Reject", List.of(FormDialog.text("note", "Reason", true)),
                        b -> () -> { b.put("approve", false); return ApiClient.post("/api/admin/sanitization/" + J.l(n, "id") + "/verify", b); }, r -> v.reload()));
        return v;
    }

    public static ResourceView config() {
        ResourceView v = new ResourceView("Scoring configuration", "Weights (w.*, bid.w.*) and pathway thresholds (t.*) for the Circular Value Score and Circular Bid Score. Changes apply to new calculations and are audited.", () -> ApiClient.get("/api/admin/config"));
        v.col("Key", "key").col("Value", n -> J.s(n, "value")).col("Meaning", "description")
                .action("Edit value", n -> true, n -> FormDialog.show("Edit " + J.s(n, "key"), "Save", List.of(FormDialog.num("value", "New value", true, J.s(n, "value"))), b -> () -> ApiClient.put("/api/admin/config/" + J.s(n, "key"), b), r -> v.reload()));
        return v;
    }

    public static ResourceView factors() {
        ResourceView v = new ResourceView("Environmental conversion factors", "kg CO2e avoided per kg handled, and metals recovery %. These produce ESTIMATES; each impact record stores the inputs used.", () -> ApiClient.get("/api/admin/env-factors"));
        v.col("Category", "categoryName").col("Reuse", "carbonReusePerKg").col("Repair", "carbonRepairPerKg").col("Refurbish", "carbonRefurbishPerKg").col("Recycle", "carbonRecyclePerKg").col("Metals %", "metalsRecoveryPct").col("Source", "sourceNote")
                .action("Edit", n -> true, n -> FormDialog.show("Factors for " + J.s(n, "categoryName"), "Save", List.of(FormDialog.num("carbonReusePerKg", "Reuse (kg CO2e/kg)", true, J.s(n, "carbonReusePerKg")), FormDialog.num("carbonRepairPerKg", "Repair", true, J.s(n, "carbonRepairPerKg")),
                        FormDialog.num("carbonRefurbishPerKg", "Refurbish", true, J.s(n, "carbonRefurbishPerKg")), FormDialog.num("carbonRecyclePerKg", "Recycle", true, J.s(n, "carbonRecyclePerKg")),
                        FormDialog.num("metalsRecoveryPct", "Metals recovery % (recycle)", true, J.s(n, "metalsRecoveryPct")), FormDialog.text("sourceNote", "Source / methodology note", false, J.s(n, "sourceNote"))),
                        b -> () -> { b.put("categoryId", J.l(n, "categoryId")); return ApiClient.put("/api/admin/env-factors", b); }, r -> v.reload()));
        return v;
    }

    public static ResourceView orders() {
        ResourceView v = new ResourceView("Transaction monitoring", "Latest 500 orders across the platform", () -> ApiClient.get("/api/admin/orders"));
        v.col("#", "id").col("Product", "listingName").col("Buyer", "buyerName").col("Seller", "sellerName").money("Amount", "amount").badge("Order", "status").badge("Payment", "paymentStatus").col("Receipt", "receiptNo").yesNo("Hazard-blocked", "shippingBlocked").date("Created", "createdAt");
        return v;
    }

    public static ResourceView audit() {
        TextField q = new TextField(); q.setPromptText("Filter by action, e.g. REFUND");
        ResourceView[] ref = new ResourceView[1];
        ref[0] = new ResourceView("Audit log", "Append-only record of sensitive actions (enforced by a database trigger)", () -> ApiClient.get("/api/admin/audit" + Q.of("q", q.getText())));
        ResourceView v = ref[0]; q.setOnAction(e -> v.reload());
        v.node(q).date("When", "createdAt").col("Actor", "actorName").col("Action", "action").col("Entity", n -> J.s(n, "entityType") + " #" + J.s(n, "entityId")).col("Detail", "detail");
        return v;
    }
}
