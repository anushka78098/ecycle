package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.component.Charts;
import com.ecycle.frontend.component.UI;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.geometry.Insets;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** Feature 4: environmental impact with the estimate/measured distinction made explicit. */
public class ImpactView extends VBox {
    public ImpactView(String scope) {
        setSpacing(14); setPadding(new Insets(20)); getChildren().add(UI.label("Loading...", "muted"));
        String path = "global".equals(scope) ? "/api/impact/global" : "/api/impact/mine?as=" + scope;
        Async.run(() -> ApiClient.get(path), this::render);
    }
    private void render(JsonNode d) {
        VBox body = new VBox(14);
        FlowPane stats = UI.flow(14);
        stats.getChildren().addAll(UI.statCard("E-waste diverted", String.format("%,.1f kg", J.d(d, "ewasteDivertedKg")), "estimate"), UI.statCard("CO2e emissions avoided", String.format("%,.1f kg", J.d(d, "carbonAvoidedKg")), "estimate"),
                UI.statCard("Materials recovered", String.format("%,.1f kg", J.d(d, "materialsRecoveredKg")), "estimate"), UI.statCard("Products reused", String.valueOf(J.l(d, "reused")), null),
                UI.statCard("Products repaired", String.valueOf(J.l(d, "repaired")), null), UI.statCard("Products refurbished", String.valueOf(J.l(d, "refurbished")), null),
                UI.statCard("Products recycled", String.valueOf(J.l(d, "recycled")), null), UI.statCard("Disposed", String.valueOf(J.l(d, "disposed")), null));
        var pathways = J.obj(); pathways.put("Reuse", J.l(d, "reused")); pathways.put("Repair", J.l(d, "repaired")); pathways.put("Refurbish", J.l(d, "refurbished")); pathways.put("Recycle", J.l(d, "recycled")); pathways.put("Dispose", J.l(d, "disposed"));
        var split = J.obj(); split.put("Estimated records", J.l(d, "estimatedRecords")); split.put("Measured-weight records", J.l(d, "measuredRecords"));
        FlowPane charts = UI.flow(14); charts.getChildren().addAll(Charts.auto("Outcomes by pathway", pathways), Charts.auto("Estimate vs measured records", split));
        body.getChildren().addAll(UI.pageHeader("Environmental impact", "Computed from outcomes recorded on delivered products"), stats, charts, UI.card(UI.label("Methodology", "section-title"), UI.label(J.s(d, "note"), "muted"),
                UI.label("Factors are configured by admins (Environmental factors). The inputs and formula of every calculation are stored with its record so results can be audited.", "muted")));
        getChildren().setAll(UI.scroll(body));
    }
}
