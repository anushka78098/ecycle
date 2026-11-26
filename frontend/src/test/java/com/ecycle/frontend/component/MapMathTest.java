package com.ecycle.frontend.component;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MapMathTest {
    @Test void projectionRoundTrips() {
        for (int z : new int[]{3, 8, 14}) { double x = MapMath.lonToX(75.85, z), y = MapMath.latToY(30.9, z); assertEquals(75.85, MapMath.xToLon(x, z), 1e-9); assertEquals(30.9, MapMath.yToLat(y, z), 1e-9); }
    }
    @Test void knownTile() { // OSM: lon 0, lat 0 is the centre of the world; at zoom 1 it is the corner of tiles (1,1)
        assertEquals(256, MapMath.lonToX(0, 1), 1e-9); assertEquals(256, MapMath.latToY(0, 1), 1e-9); assertEquals(0, MapMath.lonToX(-180, 5), 1e-9);
    }
    @Test void wrapAndClamp() { assertEquals(3, MapMath.wrapTile(-1, 2)); assertEquals(0, MapMath.wrapTile(4, 2)); assertEquals(MapMath.MAX_ZOOM, MapMath.clampZoom(99)); assertEquals(MapMath.MIN_ZOOM, MapMath.clampZoom(-3)); }
    @Test void fitZoomKeepsAllPointsInView() {
        int z = MapMath.fitZoom(30.3, 75.8, 30.9, 76.9, 800, 500, 40, 15);
        assertTrue(Math.abs(MapMath.lonToX(76.9, z) - MapMath.lonToX(75.8, z)) <= 720 && Math.abs(MapMath.latToY(30.3, z) - MapMath.latToY(30.9, z)) <= 420);
        assertTrue(MapMath.fitZoom(30.3, 75.8, 30.9, 76.9, 800, 500, 40, 15) >= MapMath.fitZoom(10, 60, 40, 90, 800, 500, 40, 15), "wider area -> lower zoom");
    }
}
