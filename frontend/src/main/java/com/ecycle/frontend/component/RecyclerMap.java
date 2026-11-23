package com.ecycle.frontend.component;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

/**
 * Interactive map built from OpenStreetMap raster tiles (drag to pan, wheel / buttons to zoom, click a marker to select). Native JavaFX nodes only, so no WebView or API key is needed.
 * The tile server is configurable with ECYCLE_TILE_URL ({z}/{x}/{y}); tiles are fetched in the background and cached in memory. Requires internet access for the base map;
 * markers and list views work without it. Map data (c) OpenStreetMap contributors.
 */
public final class RecyclerMap extends StackPane {
    private static final String TILE_URL = System.getenv().getOrDefault("ECYCLE_TILE_URL", "https://tile.openstreetmap.org/{z}/{x}/{y}.png");
    private static final Map<String, Image> CACHE = new java.util.LinkedHashMap<>(256, 0.75f, true) { @Override protected boolean removeEldestEntry(Map.Entry<String, Image> e) { return size() > 400; } };

    private final Pane tiles = new Pane(), markers = new Pane();
    private double centerLat = 30.9, centerLon = 75.85; private int zoom = 8;
    private double dragX, dragY; private boolean dragged;
    private List<JsonNode> points = new ArrayList<>(); private long selectedId = -1; private Consumer<JsonNode> onSelect = n -> { };
    private double userLat = Double.NaN, userLon = Double.NaN;
    private final Label attribution = new Label("(c) OpenStreetMap contributors");

    public RecyclerMap() {
        setMinSize(300, 300); setStyle("-fx-background-color:#dfe9e3;");
        Rectangle clip = new Rectangle(); clip.widthProperty().bind(widthProperty()); clip.heightProperty().bind(heightProperty()); setClip(clip);
        tiles.setMouseTransparent(true); markers.setPickOnBounds(false);
        Button plus = new Button("+"), minus = new Button("-"), fit = new Button("Fit");
        plus.setOnAction(e -> zoomBy(1)); minus.setOnAction(e -> zoomBy(-1)); fit.setOnAction(e -> fit());
        VBox ctl = new VBox(4, plus, minus, fit); ctl.setPadding(new Insets(8)); ctl.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE); StackPane.setAlignment(ctl, javafx.geometry.Pos.TOP_LEFT);
        attribution.setStyle("-fx-background-color:rgba(255,255,255,.8);-fx-font-size:10;-fx-padding:1 4;"); StackPane.setAlignment(attribution, javafx.geometry.Pos.BOTTOM_RIGHT);
        getChildren().addAll(tiles, markers, ctl, attribution);
        widthProperty().addListener((o, a, b) -> redraw()); heightProperty().addListener((o, a, b) -> redraw());
        setOnMousePressed(e -> { dragX = e.getX(); dragY = e.getY(); dragged = false; });
        setOnMouseDragged(e -> {
            double dx = e.getX() - dragX, dy = e.getY() - dragY; if (Math.abs(dx) + Math.abs(dy) > 2) dragged = true;
            double cx = MapMath.lonToX(centerLon, zoom) - dx, cy = MapMath.latToY(centerLat, zoom) - dy;
            centerLon = MapMath.xToLon(cx, zoom); centerLat = Math.max(-84, Math.min(84, MapMath.yToLat(cy, zoom))); dragX = e.getX(); dragY = e.getY(); redraw();
        });
        setOnScroll(e -> { if (e.getDeltaY() != 0) zoomBy(e.getDeltaY() > 0 ? 1 : -1); });
    }

    public void onSelect(Consumer<JsonNode> c) { this.onSelect = c; }
    public void setUserLocation(double lat, double lon) { userLat = lat; userLon = lon; redraw(); }
    public void setPoints(List<JsonNode> list) { this.points = list; fit(); }
    public void select(long id, boolean center) {
        selectedId = id;
        if (center) points.stream().filter(p -> p.path("id").asLong() == id).findFirst().ifPresent(p -> { centerLat = p.path("latitude").asDouble(); centerLon = p.path("longitude").asDouble(); });
        redraw();
    }
    public void fit() {
        if (points.isEmpty()) { redraw(); return; }
        double minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
        for (JsonNode p : points) { double la = p.path("latitude").asDouble(), lo = p.path("longitude").asDouble(); minLat = Math.min(minLat, la); maxLat = Math.max(maxLat, la); minLon = Math.min(minLon, lo); maxLon = Math.max(maxLon, lo); }
        centerLat = (minLat + maxLat) / 2; centerLon = (minLon + maxLon) / 2;
        zoom = points.size() == 1 ? 13 : MapMath.fitZoom(minLat, minLon, maxLat, maxLon, Math.max(getWidth(), 300), Math.max(getHeight(), 300), 40, 15);
        redraw();
    }
    private void zoomBy(int d) { zoom = MapMath.clampZoom(zoom + d); redraw(); }

    private void redraw() {
        double w = getWidth(), h = getHeight(); if (w <= 0 || h <= 0) return;
        double cx = MapMath.lonToX(centerLon, zoom), cy = MapMath.latToY(centerLat, zoom), left = cx - w / 2, top = cy - h / 2;
        tiles.getChildren().clear(); markers.getChildren().clear();
        int n = 1 << zoom;
        for (int tx = (int) Math.floor(left / MapMath.TILE); tx <= (int) Math.floor((left + w) / MapMath.TILE); tx++)
            for (int ty = (int) Math.floor(top / MapMath.TILE); ty <= (int) Math.floor((top + h) / MapMath.TILE); ty++) {
                if (ty < 0 || ty >= n) continue;
                ImageView iv = new ImageView(tile(zoom, MapMath.wrapTile(tx, zoom), ty)); iv.setX(tx * MapMath.TILE - left); iv.setY(ty * MapMath.TILE - top); tiles.getChildren().add(iv);
            }
        for (JsonNode p : points) {
            double x = MapMath.lonToX(p.path("longitude").asDouble(), zoom) - left, y = MapMath.latToY(p.path("latitude").asDouble(), zoom) - top;
            if (x < -20 || y < -20 || x > w + 20 || y > h + 20) continue;
            boolean sel = p.path("id").asLong() == selectedId;
            Circle c = new Circle(x, y, sel ? 11 : 8, Color.web(sel ? "#1b7f4b" : "#2e9e63")); c.setStroke(Color.WHITE); c.setStrokeWidth(2);
            c.setOnMouseReleased(e -> { if (!dragged) { selectedId = p.path("id").asLong(); onSelect.accept(p); redraw(); } e.consume(); });
            Text t = new Text(x + 12, y + 4, p.path("facilityName").asText()); t.setStyle("-fx-font-size:11;-fx-font-weight:bold;"); t.setMouseTransparent(true); t.setStroke(Color.WHITE); t.setStrokeWidth(3); t.setFill(Color.BLACK);
            Text t2 = new Text(t.getX(), t.getY(), t.getText()); t2.setStyle(t.getStyle()); t2.setMouseTransparent(true);
            markers.getChildren().addAll(t, t2, c);
        }
        if (!Double.isNaN(userLat)) {
            Circle me = new Circle(MapMath.lonToX(userLon, zoom) - left, MapMath.latToY(userLat, zoom) - top, 6, Color.web("#1f6feb")); me.setStroke(Color.WHITE); me.setStrokeWidth(2); me.setMouseTransparent(true); markers.getChildren().add(me);
        }
    }

    private static Image tile(int z, int x, int y) {
        String url = TILE_URL.replace("{z}", String.valueOf(z)).replace("{x}", String.valueOf(x)).replace("{y}", String.valueOf(y));
        synchronized (CACHE) { return CACHE.computeIfAbsent(url, u -> new Image(u, MapMath.TILE, MapMath.TILE, false, true, true)); }
    }
}
