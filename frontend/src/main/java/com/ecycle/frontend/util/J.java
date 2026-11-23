package com.ecycle.frontend.util;

import com.ecycle.frontend.api.ApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/** JSON/format helpers: dotted-path access, money/date/enum formatting, request-body building. */
public final class J {
    private J() {}
    private static final NumberFormat INR = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    private static final DateTimeFormatter DT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault());

    public static JsonNode at(JsonNode n, String path) {
        JsonNode c = n; for (String p : path.split("\\.")) { if (c == null) return null; c = c.path(p); }
        return c == null || c.isMissingNode() || c.isNull() ? null : c;
    }
    public static String s(JsonNode n, String path) { JsonNode v = at(n, path); return v == null ? "" : v.asText(); }
    public static double d(JsonNode n, String path) { JsonNode v = at(n, path); return v == null ? 0 : v.asDouble(); }
    public static long l(JsonNode n, String path) { JsonNode v = at(n, path); return v == null ? 0 : v.asLong(); }
    public static boolean b(JsonNode n, String path) { JsonNode v = at(n, path); return v != null && v.asBoolean(); }
    public static String money(double v) { return INR.format(v); }
    public static String money(JsonNode n, String path) { JsonNode v = at(n, path); return v == null ? "" : money(v.asDouble()); }
    public static String date(String iso) { if (iso == null || iso.isBlank()) return ""; try { return DT.format(Instant.parse(iso)); } catch (RuntimeException e) { return iso; } }
    public static String date(JsonNode n, String path) { return date(s(n, path)); }
    /** FULLY_WORKING -> "Fully working" */
    public static String human(String e) { if (e == null || e.isBlank()) return ""; String t = e.replace('_', ' ').toLowerCase(Locale.ROOT); return Character.toUpperCase(t.charAt(0)) + t.substring(1); }
    public static ObjectNode obj() { return ApiClient.mapper().createObjectNode(); }
}
