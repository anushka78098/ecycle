package com.ecycle.frontend.view;

import com.ecycle.frontend.component.UI;
import com.ecycle.frontend.navigation.Nav;
import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.GridPane;

public final class CompareDialog {
    private CompareDialog() {}
    public static void show(JsonNode items) {
        Map<String, Function<JsonNode, String>> rows = new LinkedHashMap<>();
        rows.put("Price", n -> J.money(n, "expectedPrice")); rows.put("Category", n -> J.s(n, "categoryName")); rows.put("Brand / model", n -> (J.s(n, "brand") + " " + J.s(n, "model")).trim());
        rows.put("Age (months)", n -> J.s(n, "ageMonths")); rows.put("Condition", n -> J.human(J.s(n, "physicalCondition"))); rows.put("Function", n -> J.human(J.s(n, "functionalStatus")));
        rows.put("Quantity (lot)", n -> J.s(n, "quantity")); rows.put("Weight per unit (kg)", n -> J.s(n, "weightKg")); rows.put("Location", n -> J.s(n, "pickupCity"));
        rows.put("Circular Value Score", n -> String.format("%.1f / 100", J.d(n, "score.overall"))); rows.put("Recommended pathway", n -> J.human(J.s(n, "score.recommendedPathway")));
        rows.put("Repairability", n -> String.format("%.0f", J.d(n, "score.repairability"))); rows.put("Component recovery", n -> String.format("%.0f", J.d(n, "score.componentRecovery")));
        rows.put("Remaining life", n -> String.format("%.0f", J.d(n, "score.remainingLife"))); rows.put("Hazard level", n -> J.human(J.s(n, "hazardLevel")));
        rows.put("Data sanitization", n -> J.b(n, "requiresSanitization") ? (J.b(n, "sanitizationVerified") ? "Verified" : "Required, not yet verified") : "Not required");
        rows.put("Sale mode", n -> J.b(n, "auctionEnabled") ? "Reverse auction" : "Buy now / offers"); rows.put("Seller", n -> J.s(n, "sellerName") + (J.b(n, "sellerVerified") ? " (verified)" : ""));
        GridPane g = new GridPane(); g.setHgap(18); g.setVgap(8); g.setPadding(new Insets(12));
        for (int c = 0; c < items.size(); c++) g.add(UI.label(J.s(items.get(c), "name"), "section-title"), c + 1, 0);
        int r = 1;
        for (var e : rows.entrySet()) {
            g.add(UI.label(e.getKey(), "muted"), 0, r);
            for (int c = 0; c < items.size(); c++) g.add(UI.label(e.getValue().apply(items.get(c)), "label"), c + 1, r);
            r++;
        }
        Dialog<ButtonType> d = new Dialog<>(); d.setTitle("Compare products"); d.getDialogPane().getStylesheets().add(Nav.css());
        d.getDialogPane().setContent(UI.scroll(g)); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.getDialogPane().setPrefSize(960, 620); d.setResizable(true); d.show();
    }
}
