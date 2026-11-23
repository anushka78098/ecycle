package com.ecycle.frontend.component;

import com.ecycle.frontend.api.ApiClient;
import com.ecycle.frontend.util.Async;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads authenticated images (the API needs the bearer token, so JavaFX cannot fetch them by URL) off the UI thread and caches the bytes. */
public final class ImageCache {
    private static final Logger log = LoggerFactory.getLogger(ImageCache.class);
    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();
    private ImageCache() {}

    public static ImageView view(String apiPath, double w, double h) {
        ImageView iv = new ImageView(); iv.setFitWidth(w); iv.setFitHeight(h); iv.setPreserveRatio(true); iv.setSmooth(true);
        byte[] cached = CACHE.get(apiPath);
        if (cached != null) { iv.setImage(new Image(new ByteArrayInputStream(cached), w * 2, h * 2, true, true)); return iv; }
        Async.run(() -> ApiClient.bytes(apiPath), bytes -> { CACHE.put(apiPath, bytes); iv.setImage(new Image(new ByteArrayInputStream(bytes), w * 2, h * 2, true, true)); },
                t -> log.warn("Could not load image {}: {}", apiPath, t.getMessage()));
        return iv;
    }
}
