package com.ecycle.frontend.component;

/** Web-Mercator slippy-map maths (OpenStreetMap tile scheme). Pure functions, unit-tested without JavaFX. */
public final class MapMath {
    private MapMath() {}
    public static final int TILE = 256, MIN_ZOOM = 2, MAX_ZOOM = 18;
    public static double worldSize(int zoom) { return TILE * Math.pow(2, zoom); }
    /** Pixel X of a longitude in the world at the given zoom. */
    public static double lonToX(double lon, int zoom) { return (lon + 180.0) / 360.0 * worldSize(zoom); }
    public static double latToY(double lat, int zoom) {
        double l = Math.max(-85.0511, Math.min(85.0511, lat)), r = Math.toRadians(l);
        return (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * worldSize(zoom);
    }
    public static double xToLon(double x, int zoom) { return x / worldSize(zoom) * 360.0 - 180.0; }
    public static double yToLat(double y, int zoom) { double n = Math.PI - 2 * Math.PI * y / worldSize(zoom); return Math.toDegrees(Math.atan(Math.sinh(n))); }
    public static int clampZoom(int z) { return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z)); }
    /** Wraps a tile column into [0, 2^zoom). */
    public static int wrapTile(int x, int zoom) { int n = 1 << zoom; return ((x % n) + n) % n; }
    /** Largest zoom at which all points fit in a view of the given size (with margin px), capped at maxZoom. */
    public static int fitZoom(double minLat, double minLon, double maxLat, double maxLon, double w, double h, double margin, int maxZoom) {
        for (int z = Math.min(maxZoom, MAX_ZOOM); z > MIN_ZOOM; z--) {
            double dx = Math.abs(lonToX(maxLon, z) - lonToX(minLon, z)), dy = Math.abs(latToY(minLat, z) - latToY(maxLat, z));
            if (dx <= w - 2 * margin && dy <= h - 2 * margin) return z;
        }
        return MIN_ZOOM;
    }
}
