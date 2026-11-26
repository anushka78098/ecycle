package com.ecycle.frontend.view;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.api.Session;
import com.ecycle.frontend.component.*;
import com.ecycle.frontend.component.FormDialog.Opt;
import com.ecycle.frontend.util.Alerts;
import com.ecycle.frontend.util.Async;
import com.ecycle.frontend.util.J;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Interactive recycler map (find facilities, book a pickup) and facility management for recyclers. */
public final class RecyclerViews {
    private RecyclerViews() {}

    public static Node map() {
        RecyclerMap map = new RecyclerMap();
        ListView<JsonNode> list = new ListView<>(); list.setPrefWidth(340);
        list.setCellFactory(lv -> new ListCell<>() { @Override protected void updateItem(JsonNode n, boolean empty) {
            super.updateItem(n, empty);
            setText(empty || n == null ? null : n.path("facilityName").asText() + "\n" + n.path("city").asText() + (n.path("distanceKm").isNull() ? "" : "  -  " + n.path("distanceKm").asDouble() + " km") + "\nAccepts: " + n.path("acceptedCategories").asText("-")); } });
        Label detail = UI.label("Select a facility on the map or in the list.", "muted");
        Button book = UI.primary("Book a pickup here", () -> { JsonNode s = list.getSelectionModel().getSelectedItem(); if (s != null) bookDialog(s); });
        book.setDisable(true);
        TextField city = new TextField(); city.setPromptText("Filter by city"); TextField cat = new TextField(); cat.setPromptText("Accepted category");
        TextField lat = new TextField(); lat.setPromptText("My latitude"); lat.setPrefWidth(110); TextField lon = new TextField(); lon.setPromptText("My longitude"); lon.setPrefWidth(110);
        Runnable[] load = new Runnable[1];
        load[0] = () -> {
            StringBuilder q = new StringBuilder("/api/recyclers?x=1"); boolean geo = false;
            try { if (!lat.getText().isBlank() && !lon.getText().isBlank()) { double a = Double.parseDouble(lat.getText().trim()), b = Double.parseDouble(lon.getText().trim()); q.append("&lat=").append(a).append("&lng=").append(b).append("&radiusKm=200"); map.setUserLocation(a, b); geo = true; } }
            catch (NumberFormatException e) { Alerts.info("Latitude and longitude must be numbers, e.g. 30.9 and 75.85."); return; }
            if (!cat.getText().isBlank()) q.append("&category=").append(java.net.URLEncoder.encode(cat.getText().trim(), java.nio.charset.StandardCharsets.UTF_8));
            String cf = city.getText().trim().toLowerCase();
            Async.run(() -> ApiClient.get(q.toString()), data -> {
                List<JsonNode> rows = new ArrayList<>(); data.forEach(n -> { if (cf.isEmpty() || n.path("city").asText().toLowerCase().contains(cf)) rows.add(n); });
                list.setItems(FXCollections.observableArrayList(rows)); map.setPoints(rows);
            });
        };
        list.getSelectionModel().selectedItemProperty().addListener((o, a, n) -> { if (n != null) { map.select(n.path("id").asLong(), true); detail.setText(describe(n)); book.setDisable(false); } });
        map.onSelect(n -> { list.getItems().stream().filter(x -> x.path("id").asLong() == n.path("id").asLong()).findFirst().ifPresent(x -> list.getSelectionModel().select(x)); });
        Button search = UI.secondary("Search", () -> load[0].run());
        HBox filters = new HBox(8, city, cat, lat, lon, search); filters.setPadding(new Insets(0, 0, 4, 0));
        VBox side = new VBox(8, list, detail, book); VBox.setVgrow(list, Priority.ALWAYS); side.setPrefWidth(350);
        BorderPane bp = new BorderPane(map); bp.setRight(side); BorderPane.setMargin(side, new Insets(0, 0, 0, 12));
        VBox root = new VBox(10, UI.pageHeader("Recycler map", "Find certified recyclers near you and book a pickup. Drag to pan, scroll to zoom, click a marker."), filters, bp);
        VBox.setVgrow(bp, Priority.ALWAYS); root.setPadding(new Insets(20)); load[0].run();
        return root;
    }

    private static String describe(JsonNode n) {
        return n.path("facilityName").asText() + "\n" + n.path("address").asText() + ", " + n.path("city").asText() + "\nRecycler: " + J.s(n, "recyclerName") + (J.s(n, "organization").isBlank() ? "" : " (" + J.s(n, "organization") + ")")
                + "\nHours: " + J.s(n, "openingHours") + "\nPhone: " + J.s(n, "phone") + "\nCertifications: " + J.s(n, "certifications") + "\nService radius: " + J.l(n, "serviceRadiusKm") + " km"
                + "\nRecovery capability: " + J.l(n, "recoveryCapability") + "/100   Environmental rating: " + J.l(n, "environmentalRating") + "/100";
    }

    /** Booking dialog; the item can optionally be linked to one of the user's own listings (sellers) so the passport and custody chain follow it. */
    public static void bookDialog(JsonNode facility) {
        Async.run(() -> { try { return Session.is("SELLER") ? ApiClient.get("/api/listings/mine") : ApiClient.mapper().createArrayNode(); } catch (RuntimeException e) { return ApiClient.mapper().createArrayNode(); } }, mine -> {
            List<Opt> items = new ArrayList<>(); items.add(new Opt("", "Not linked to a listing"));
            mine.forEach(l -> { String st = l.path("status").asText(); if (!"SOLD".equals(st) && !"WITHDRAWN".equals(st) && !"REJECTED".equals(st)) items.add(new Opt(l.path("id").asText(), "#" + l.path("id").asText() + " " + l.path("name").asText())); });
            FormDialog.show("Book a pickup at " + facility.path("facilityName").asText(), "Request pickup", List.of(
                    FormDialog.combo("listingId", "Linked listing", false, items, ""),
                    FormDialog.text("itemsDescription", "Items", true), FormDialog.integer("quantity", "Quantity", true, "1"), FormDialog.num("estWeightKg", "Estimated total weight (kg)", true, "2"),
                    FormDialog.check("dataBearing", "Devices store personal data (phones, laptops, drives)", false), FormDialog.check("hazardous", "Damaged / swollen batteries or other hazards", false),
                    FormDialog.text("pickupAddress", "Pickup address", false), FormDialog.text("contactPhone", "Contact phone", false),
                    FormDialog.text("preferredDate", "Preferred date (YYYY-MM-DD)", true, LocalDate.now().plusDays(2).toString())),
                    b -> { String d = b.path("preferredDate").asText(); b.remove("preferredDate");
                        try { b.put("preferredAt", LocalDate.parse(d).atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toString()); } catch (RuntimeException e) { return () -> { throw new com.ecycle.frontend.api.ApiError(400, "Date must look like 2026-12-01", null); }; }
                        b.put("locationId", facility.path("id").asLong()); if (b.path("listingId").asText().isBlank()) b.remove("listingId"); else b.put("listingId", b.path("listingId").asLong());
                        return () -> ApiClient.post("/api/pickups", b); },
                    r -> Alerts.info("Pickup requested. Tracking code " + r.path("trackingCode").asText() + ". You will see live status updates under 'My pickups'."));
        });
    }

    /** Facility management for recycler accounts (and admins). */
    public static ResourceView facilities() {
        ResourceView v = new ResourceView("My facilities", "Locations shown on the recycler map. Coordinates must be exact (right-click a place in any map app to copy them).", () -> ApiClient.get("/api/recyclers/mine"));
        v.col("Name", "facilityName").col("Address", "address").col("City", "city").col("Lat", n -> String.format("%.5f", J.d(n, "latitude"))).col("Lon", n -> String.format("%.5f", J.d(n, "longitude")))
                .col("Accepts", "acceptedCategories").yesNo("Active", "active")
                .tool("Add facility", () -> form(null, v)).action("Edit", n -> true, n -> form(n, v)).onOpen(n -> form(n, v));
        return v;
    }
    private static void form(JsonNode n, ResourceView v) {
        boolean e = n != null;
        FormDialog.show(e ? "Edit facility" : "Add facility", "Save", List.of(
                FormDialog.text("facilityName", "Facility name", true, e ? J.s(n, "facilityName") : null), FormDialog.text("address", "Street address", true, e ? J.s(n, "address") : null), FormDialog.text("city", "City", true, e ? J.s(n, "city") : null),
                FormDialog.num("latitude", "Latitude (-90..90)", true, e ? J.s(n, "latitude") : "30.9"), FormDialog.num("longitude", "Longitude (-180..180)", true, e ? J.s(n, "longitude") : "75.85"),
                FormDialog.text("phone", "Phone", false, e ? J.s(n, "phone") : null), FormDialog.text("openingHours", "Opening hours", false, e ? J.s(n, "openingHours") : "Mon-Sat 09:00-18:00"),
                FormDialog.text("acceptedCategories", "Accepted categories (comma separated)", false, e ? J.s(n, "acceptedCategories") : "Laptops, Smartphones, Batteries"),
                FormDialog.text("certifications", "Certifications", false, e ? J.s(n, "certifications") : null), FormDialog.integer("serviceRadiusKm", "Service radius (km)", true, e ? J.s(n, "serviceRadiusKm") : "25"),
                FormDialog.check("active", "Active (visible on the map)", !e || J.b(n, "active"))),
                b -> () -> e ? ApiClient.put("/api/recyclers/" + J.l(n, "id"), b) : ApiClient.post("/api/recyclers", b), r -> v.reload());
    }
}
