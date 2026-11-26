package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.api.Q;
import com.ecycle.frontend.component.FormDialog;
import com.ecycle.frontend.component.FormDialog.Opt;
import com.ecycle.frontend.component.ProductCard;
import com.ecycle.frontend.component.UI;
import com.ecycle.frontend.util.Async;
import com.ecycle.shared.Enums;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Grid marketplace with search, filters, sorting, wishlist and product comparison. */
public class MarketplaceView extends VBox {
    private final TextField q = new TextField(), brand = new TextField(), location = new TextField(), minP = new TextField(), maxP = new TextField();
    private final ComboBox<Opt> category = new ComboBox<>(), condition = new ComboBox<>(), functional = new ComboBox<>(), pathway = new ComboBox<>(), sort = new ComboBox<>();
    private int page = 0;
    private final FlowPane grid = UI.flow(16); private final Label status = UI.label("", "muted");
    private final Button more = UI.secondary("Load more", () -> { page++; search(true); });
    private final Set<Long> wished = new HashSet<>(); private final Map<Long, JsonNode> compare = new LinkedHashMap<>();

    public MarketplaceView(String initialQuery) {
        setSpacing(12); setPadding(new Insets(20));
        q.setText(initialQuery == null ? "" : initialQuery); q.setPromptText("Search name, brand, model...");
        brand.setPromptText("Brand"); location.setPromptText("City"); minP.setPromptText("Min price"); maxP.setPromptText("Max price");
        category.getItems().add(new Opt("", "All categories")); category.setValue(category.getItems().get(0));
        fill(condition, "Any condition", Enums.ItemCondition.class); fill(functional, "Any function", Enums.FunctionalStatus.class); fill(pathway, "Any pathway", Enums.Pathway.class);
        sort.getItems().addAll(new Opt("newest", "Newest"), new Opt("price_asc", "Price: low to high"), new Opt("price_desc", "Price: high to low"), new Opt("score", "Circular score")); sort.setValue(sort.getItems().get(0));
        for (TextField t : List.of(q, brand, location, minP, maxP)) t.setOnAction(e -> search(false));
        q.setPrefWidth(220); minP.setPrefWidth(90); maxP.setPrefWidth(90); brand.setPrefWidth(110); location.setPrefWidth(110);
        FlowPane filters = new FlowPane(8, 8, q, category, condition, functional, pathway, brand, location, minP, maxP, sort, UI.primary("Search", () -> search(false)),
                UI.secondary("Compare selected", this::openCompare));
        VBox bar = UI.card(filters); bar.setPadding(new Insets(12));
        ScrollPane sp = UI.scroll(new VBox(10, grid, more, status)); VBox.setVgrow(sp, Priority.ALWAYS);
        getChildren().addAll(UI.pageHeader("Marketplace", "Verified reusable electronics, components and recyclable material, ranked by Circular Value Score"), bar, sp);
        Async.run(() -> ApiClient.get("/api/categories"), cats -> cats.forEach(c -> category.getItems().add(new Opt(c.path("id").asText(), c.path("name").asText()))));
        Async.run(() -> ApiClient.get("/api/wishlist/ids"), ids -> { ids.forEach(i -> wished.add(i.asLong())); search(false); });
    }
    private static <E extends Enum<E>> void fill(ComboBox<Opt> c, String any, Class<E> e) {
        c.getItems().add(new Opt("", any)); for (E v : e.getEnumConstants()) c.getItems().add(new Opt(v.name(), com.ecycle.frontend.util.J.human(v.name()))); c.setValue(c.getItems().get(0));
    }
    private static String v(ComboBox<Opt> c) { return c.getValue() == null ? null : c.getValue().value(); }

    private void search(boolean append) {
        if (!append) { page = 0; grid.getChildren().clear(); }
        String qs = Q.of("q", q.getText(), "categoryId", v(category), "condition", v(condition), "functional", v(functional), "pathway", v(pathway), "brand", brand.getText(),
                "location", location.getText(), "minPrice", minP.getText(), "maxPrice", maxP.getText(), "sort", v(sort), "page", page, "size", 24);
        status.setText("Searching...");
        Async.run(() -> ApiClient.get("/api/listings" + qs), res -> {
            for (JsonNode l : res) grid.getChildren().add(card(l));
            more.setVisible(res.size() == 24); more.setManaged(res.size() == 24);
            status.setText(grid.getChildren().isEmpty() ? "No products match these filters." : "");
        });
    }
    private ProductCard card(JsonNode l) {
        long id = l.path("id").asLong();
        ProductCard[] ref = new ProductCard[1];
        ref[0] = new ProductCard(l, n -> new ProductDialog(id).show(), sel -> { if (sel) compare.put(id, l); else compare.remove(id); }, wished.contains(id), () -> toggleWish(id));
        return ref[0];
    }
    private void toggleWish(long id) {
        boolean has = wished.contains(id);
        Async.run(() -> has ? ApiClient.delete("/api/wishlist/" + id) : ApiClient.post("/api/wishlist/" + id, null), r -> { if (has) wished.remove(id); else wished.add(id); search(false); });
    }
    private void openCompare() {
        if (compare.size() < 2 || compare.size() > 4) { com.ecycle.frontend.util.Alerts.info("Tick 2 to 4 products with the Compare checkbox first."); return; }
        String ids = String.join(",", compare.keySet().stream().map(String::valueOf).toList());
        Async.run(() -> ApiClient.get("/api/listings/compare?ids=" + ids), CompareDialog::show);
    }
}
