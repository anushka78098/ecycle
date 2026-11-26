package com.ecycle.frontend.api;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Query-string builder that skips null/blank values: Q.of("q", text, "page", 0). */
public final class Q {
    private Q() {}
    public static String of(Object... kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            if (v == null || v.toString().isBlank()) continue;
            sb.append(sb.length() == 0 ? '?' : '&').append(kv[i]).append('=').append(URLEncoder.encode(v.toString(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
