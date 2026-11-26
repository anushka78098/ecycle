package com.ecycle.backend.engine;

/** Great-circle distance and bounding box helpers for the recycler locator. */
public final class GeoMath {
    private GeoMath() {}
    private static final double R_KM = 6371.0088;

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }
    /** {minLat, maxLat, minLon, maxLon}; longitude span is clamped, so searches that cross the antimeridian are not supported. */
    public static double[] boundingBox(double lat, double lon, double radiusKm) {
        double dLat = Math.toDegrees(radiusKm / R_KM);
        double cos = Math.max(0.01, Math.cos(Math.toRadians(lat)));
        double dLon = Math.min(180, Math.toDegrees(radiusKm / (R_KM * cos)));
        return new double[]{Math.max(-90, lat - dLat), Math.min(90, lat + dLat), Math.max(-180, lon - dLon), Math.min(180, lon + dLon)};
    }
    public static boolean validLatLon(Double lat, Double lon) { return lat != null && lon != null && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180; }
}
