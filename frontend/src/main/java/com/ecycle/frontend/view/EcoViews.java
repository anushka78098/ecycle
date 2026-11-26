package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.component.*;
import com.ecycle.frontend.component.FormDialog.Opt;
import com.ecycle.frontend.util.Alerts;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Eco-points and gamification: sustainability dashboard, carbon estimates, challenges, badges, leaderboard and reward redemption. */
public final class EcoViews {
    private EcoViews() {}

    public static Node dashboard() {
        VBox root = new VBox(14); root.setPadding(new Insets(20));
        Async.run(() -> ApiClient.get("/api/eco/dashboard"), d -> {
            root.getChildren().clear();
            root.getChildren().add(UI.pageHeader("Sustainability dashboard", "Eco-points, carbon impact, challenges and badges. Carbon figures are estimates from average conversion factors.",
                    UI.secondary("Estimate my impact", EcoViews::estimateDialog), UI.secondary(J.b(d, "showOnLeaderboard") ? "Hide me from leaderboard" : "Show me on leaderboard", () ->
                            Async.run(() -> ApiClient.put("/api/eco/leaderboard/visibility?visible=" + !J.b(d, "showOnLeaderboard"), null), r -> { root.getChildren().clear(); root.getChildren().add(dashboard()); }))));
            FlowPane stats = UI.flow(12);
            stats.getChildren().addAll(UI.statCard("Eco-points", String.valueOf(J.l(d, "balance")), "Lifetime " + J.l(d, "lifetimePoints")), UI.statCard("Level " + J.l(d, "level"), J.s(d, "levelName"), J.l(d, "nextLevelAt") + " pts for next level"),
                    UI.statCard("Rank", J.l(d, "rank") > 0 ? "#" + J.l(d, "rank") : "n/a", J.b(d, "showOnLeaderboard") ? "on the leaderboard" : "hidden"), UI.statCard("CO2e avoided", String.format("%.1f kg", J.d(d, "carbonAvoidedKg")), "estimate"),
                    UI.statCard("E-waste diverted", String.format("%.1f kg", J.d(d, "ewasteDivertedKg")), J.l(d, "pickupsCompleted") + " pickups completed"),
                    UI.statCard("Equivalent to", String.format("%.1f tree-years", J.d(d, "equivalents.treeYears")), String.format("%.0f km by car, %d phone charges", J.d(d, "equivalents.carKm"), J.l(d, "equivalents.phoneCharges"))));
            ProgressBar pb = new ProgressBar(Math.max(0, Math.min(1, J.d(d, "levelProgress")))); pb.setMaxWidth(Double.MAX_VALUE);
            root.getChildren().addAll(stats, UI.card(UI.label("Progress to next level", "section-title"), pb));
            if (d.path("pointsByMonth").size() > 0) root.getChildren().add(UI.card(Charts.bar("Points earned per month", d.path("pointsByMonth"))));
            VBox ch = new VBox(8); ch.getChildren().add(UI.label("Challenges", "section-title"));
            for (JsonNode c : d.path("challenges")) { ProgressBar p = new ProgressBar(J.d(c, "fraction")); p.setMaxWidth(Double.MAX_VALUE);
                ch.getChildren().add(new VBox(2, UI.label(J.s(c, "title") + "  (+" + J.l(c, "rewardPoints") + " pts)" + (J.b(c, "completed") ? "  - completed" : ""), "stat-title"), UI.label(J.s(c, "description") + "  " + String.format("%.0f / %.0f", J.d(c, "progress"), J.d(c, "target")), "muted"), p)); }
            if (d.path("challenges").size() == 0) ch.getChildren().add(UI.label("No active challenges.", "muted"));
            FlowPane badges = UI.flow(10);
            for (JsonNode b : d.path("badges")) { Label l = UI.label((J.b(b, "earned") ? "[x] " : "[ ] ") + J.s(b, "name") + "\n" + J.s(b, "description") + (J.b(b, "earned") ? "" : "\n" + String.format("%.0f%%", J.d(b, "progress") / Math.max(1, J.d(b, "threshold")) * 100)), J.b(b, "earned") ? "badge-green" : "muted");
                VBox box = UI.card(l); box.setPrefWidth(210); badges.getChildren().add(box); }
            VBox act = new VBox(4); act.getChildren().add(UI.label("Recent activity", "section-title"));
            for (JsonNode a : d.path("recentActivity")) act.getChildren().add(UI.label(J.date(a, "createdAt") + "   +" + J.l(a, "delta") + " pts   " + J.s(a, "note"), "muted"));
            root.getChildren().addAll(UI.card(ch), UI.label("Badges", "section-title"), badges, UI.card(act), UI.label(J.s(d, "note"), "muted"));
        });
        return UI.scroll(root);
    }

    public static ResourceView leaderboard() {
        ResourceView v = new ResourceView("Leaderboard", "Top contributors by lifetime eco-points. Names are shortened; you can opt out on your dashboard.", () -> ApiClient.get("/api/eco/leaderboard?limit=50"));
        v.col("Rank", n -> "#" + J.l(n, "rank")).col("Name", n -> J.s(n, "name") + (J.b(n, "me") ? "  (you)" : "")).col("Level", n -> J.l(n, "level") + " - " + J.s(n, "levelName")).col("Points", "points");
        return v;
    }

    public static ResourceView rewards() {
        ResourceView v = new ResourceView("Rewards", "Redeem eco-points for vouchers and perks. Redemption is final.", () -> ApiClient.get("/api/eco/rewards"));
        v.col("Reward", "title").col("Description", "description").col("Cost (pts)", "costPoints").col("Stock", n -> n.path("stock").isNull() ? "unlimited" : J.s(n, "stock")).yesNo("Affordable", "affordable")
                .action("Redeem", n -> J.b(n, "affordable"), n -> Alerts.confirm("Spend " + J.l(n, "costPoints") + " eco-points on \"" + J.s(n, "title") + "\"?", () ->
                        Async.run(() -> ApiClient.post("/api/eco/rewards/" + J.l(n, "id") + "/redeem", null), r -> { Alerts.info("Redeemed. Voucher code: " + J.s(r, "voucherCode")); v.reload(); })))
                .tool("My redemptions", () -> {
                    ResourceView h = new ResourceView("My redemptions", null, () -> ApiClient.get("/api/eco/redemptions")); h.date("When", "createdAt").col("Reward", "rewardTitle").col("Points", "pointsSpent").col("Voucher code", "voucherCode").badge("Status", "status");
                    Dialog<ButtonType> d = new Dialog<>(); d.getDialogPane().setContent(h); h.setPrefSize(760, 420); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.show(); });
        return v;
    }

    private static void estimateDialog() {
        Async.run(() -> ApiClient.get("/api/categories"), cats -> {
            List<Opt> o = new ArrayList<>(); cats.forEach(c -> o.add(new Opt(c.path("id").asText(), c.path("name").asText())));
            FormDialog.show("Carbon-impact estimate", "Estimate", List.of(FormDialog.combo("categoryId", "Category", true, o, o.isEmpty() ? "" : o.get(0).value()), FormDialog.num("weightKg", "Total weight (kg)", true, "5"),
                    FormDialog.integer("quantity", "Items", true, "1"), FormDialog.combo("pathway", "Pathway", true, FormDialog.opts("REUSE", "REPAIR", "REFURBISH", "RECYCLE"), "RECYCLE")),
                    b -> () -> ApiClient.get("/api/eco/estimate?categoryId=" + b.path("categoryId").asText() + "&weightKg=" + b.path("weightKg").asText() + "&quantity=" + b.path("quantity").asText() + "&pathway=" + b.path("pathway").asText()),
                    r -> Alerts.info(String.format("E-waste diverted: %.1f kg%nCO2e avoided: about %.1f kg%nEco-points: about %d%nEquivalent to %.1f tree-years, %.0f km by car, %d phone charges%n%n%s", J.d(r, "ewasteDivertedKg"), J.d(r, "carbonAvoidedKg"),
                            J.l(r, "estimatedPoints"), J.d(r, "equivalents.treeYears"), J.d(r, "equivalents.carKm"), J.l(r, "equivalents.phoneCharges"), J.s(r, "methodology"))));
        });
    }

    // ---- admin
    public static Node adminRewards() {
        ResourceView v = new ResourceView("Reward catalogue", "Create and edit rewards", () -> ApiClient.get("/api/admin/eco/rewards"));
        v.col("Code", "code").col("Title", "title").col("Cost", "costPoints").col("Stock", n -> n.path("stock").isNull() ? "unlimited" : J.s(n, "stock")).yesNo("Active", "active")
                .tool("Add reward", () -> rewardForm(null, v)).action("Edit", n -> true, n -> rewardForm(n, v));
        return v;
    }
    private static void rewardForm(JsonNode n, ResourceView v) {
        boolean e = n != null;
        FormDialog.show(e ? "Edit reward" : "New reward", "Save", List.of(FormDialog.text("code", "Code", true, e ? J.s(n, "code") : null), FormDialog.text("title", "Title", true, e ? J.s(n, "title") : null), FormDialog.text("description", "Description", true, e ? J.s(n, "description") : null),
                FormDialog.integer("costPoints", "Cost (points)", true, e ? J.s(n, "costPoints") : "100"), FormDialog.integer("stock", "Stock (blank = unlimited)", false, e && !n.path("stock").isNull() ? J.s(n, "stock") : null), FormDialog.check("active", "Active", !e || J.b(n, "active"))),
                b -> () -> e ? ApiClient.put("/api/admin/eco/rewards/" + J.l(n, "id"), b) : ApiClient.post("/api/admin/eco/rewards", b), r -> v.reload());
    }
    public static Node adminChallenges() {
        ResourceView v = new ResourceView("Challenges", "Metric-based challenges with a bonus on completion", () -> ApiClient.get("/api/admin/eco/challenges"));
        v.col("Code", "code").col("Title", "title").col("Metric", n -> J.human(J.s(n, "metric"))).col("Target", "target").col("Bonus", "rewardPoints").date("Ends", "endsAt").yesNo("Active", "active")
                .tool("Add challenge", () -> challengeForm(null, v)).action("Edit", n -> true, n -> challengeForm(n, v));
        return v;
    }
    private static void challengeForm(JsonNode n, ResourceView v) {
        boolean e = n != null;
        FormDialog.show(e ? "Edit challenge" : "New challenge", "Save", List.of(FormDialog.text("code", "Code", true, e ? J.s(n, "code") : null), FormDialog.text("title", "Title", true, e ? J.s(n, "title") : null), FormDialog.text("description", "Description", true, e ? J.s(n, "description") : null),
                FormDialog.combo("metric", "Metric", true, FormDialog.opts("PICKUPS_COMPLETED", "SANITIZATIONS", "CARBON_KG", "WEIGHT_KG", "CIRCULAR_ACTIONS", "POINTS_EARNED"), e ? J.s(n, "metric") : "PICKUPS_COMPLETED"),
                FormDialog.num("target", "Target", true, e ? J.s(n, "target") : "3"), FormDialog.integer("rewardPoints", "Bonus points", true, e ? J.s(n, "rewardPoints") : "50"), FormDialog.text("endsAt", "Ends at (ISO instant, e.g. 2027-01-31T23:59:00Z; blank = none)", false, e ? J.s(n, "endsAt") : null),
                FormDialog.check("active", "Active", !e || J.b(n, "active"))),
                b -> () -> e ? ApiClient.put("/api/admin/eco/challenges/" + J.l(n, "id"), b) : ApiClient.post("/api/admin/eco/challenges", b), r -> v.reload());
    }
}
