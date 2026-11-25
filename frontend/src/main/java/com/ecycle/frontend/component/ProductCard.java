package com.ecycle.frontend.component;

import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;

/** Marketplace tile: photo, price, condition/pathway/hazard badges and Circular Value Score bar. */
public final class ProductCard extends VBox {
    public ProductCard(JsonNode l, Consumer<JsonNode> onOpen, Consumer<Boolean> onCompare, boolean wished, Runnable onWish) {
        setSpacing(8); setPadding(new Insets(10)); setPrefWidth(250); setMaxWidth(250); getStyleClass().add("product-card");
        StackPane photo = new StackPane(); photo.setPrefSize(228, 150); photo.setMinHeight(150); photo.getStyleClass().add("photo-ph");
        JsonNode imageIds = l.path("imageIds");
        long listingId = J.l(l, "id");
        if (imageIds.size() > 0) {
            photo.getChildren().add(ImageCache.view(imagePath(listingId, imageIds.get(0).asLong()), 228, 150));
        }
        else photo.getChildren().add(UI.label(J.s(l, "categoryName"), "section-title"));
        HBox photoPreviews = new HBox(4);
        photoPreviews.setAlignment(Pos.CENTER_LEFT);
        int previewCount = Math.min(3, Math.max(0, imageIds.size() - 1));
        for (int i = 1; i <= previewCount; i++) {
            long imageId = imageIds.get(i).asLong();
            var thumbnail = ImageCache.view(imagePath(listingId, imageId), 54, 40);
            thumbnail.setOnMouseClicked(e -> {
                photo.getChildren().setAll(ImageCache.view(imagePath(listingId, imageId), 228, 150));
                e.consume();
            });
            photoPreviews.getChildren().add(thumbnail);
        }
        if (imageIds.size() > previewCount + 1) photoPreviews.getChildren().add(UI.label("+" + (imageIds.size() - previewCount - 1), "muted"));
        FlowPane badges = new FlowPane(5, 5, UI.badge(J.s(l, "physicalCondition")), UI.badge(J.s(l, "functionalStatus")));
        if (!"LOW".equals(J.s(l, "hazardLevel"))) badges.getChildren().add(UI.badge(J.s(l, "hazardLevel")));
        if (J.b(l, "auctionEnabled")) badges.getChildren().add(UI.badge("AUCTION"));
        double score = J.d(l, "score.overall");
        ProgressBar bar = new ProgressBar(score / 100.0); bar.setMaxWidth(Double.MAX_VALUE);
        HBox top = new HBox(UI.label(J.s(l, "pickupCity"), "muted"), UI.grow(), UI.badge(J.s(l, "pathway")));
        top.setAlignment(Pos.CENTER_LEFT);
        var name = UI.label(J.s(l, "name"), "section-title");
        CheckBox cmp = new CheckBox("Compare"); cmp.selectedProperty().addListener((o, a, b) -> onCompare.accept(b));
        javafx.scene.control.Button heart = UI.secondary(wished ? "Saved" : "Save", onWish); heart.setStyle("-fx-padding: 3 10;");
        getChildren().add(photo);
        if (!photoPreviews.getChildren().isEmpty()) getChildren().add(photoPreviews);
        getChildren().addAll(name, UI.label((J.s(l, "brand") + " " + J.s(l, "model")).trim() + " | " + J.s(l, "categoryName"), "muted"),
                UI.label(J.money(l, "expectedPrice"), "price"), top, badges, UI.label("Circular Value Score: " + Math.round(score) + "/100", "muted"), bar, new HBox(8, cmp, UI.grow(), heart));
        photo.setOnMouseClicked(e -> onOpen.accept(l)); setOnMouseClicked(e -> { if (e.getTarget() == this) onOpen.accept(l); });
        name.setOnMouseClicked(e -> onOpen.accept(l));
    }

    private static String imagePath(long listingId, long imageId) {
        return "/api/listings/" + listingId + "/images/" + imageId;
    }
}
