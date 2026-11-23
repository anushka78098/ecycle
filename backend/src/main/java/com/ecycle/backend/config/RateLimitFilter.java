package com.ecycle.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/** Sliding-window limiter (per client address and sensitive endpoint group). In-memory: use a shared store if the API is scaled out. */
public class RateLimitFilter extends OncePerRequestFilter {
    private static final List<String> SENSITIVE = List.of("/api/auth/login", "/api/auth/register", "/api/auth/forgot", "/api/auth/reset",
            "/bids", "/api/payments", "/mock-confirm", "/verify");
    private final int perMinute;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    public RateLimitFilter(int perMinute) { this.perMinute = perMinute; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String path = req.getRequestURI();
        if ("POST".equals(req.getMethod()) && SENSITIVE.stream().anyMatch(path::contains)) {
            String group = SENSITIVE.stream().filter(path::contains).findFirst().orElse("x");
            Deque<Long> q = hits.computeIfAbsent(req.getRemoteAddr() + "|" + group, k -> new ArrayDeque<>());
            long now = System.currentTimeMillis();
            boolean allowed;
            synchronized (q) {
                while (!q.isEmpty() && now - q.peekFirst() > 60_000) q.pollFirst();
                allowed = q.size() < perMinute;
                if (allowed) q.addLast(now);
            }
            if (!allowed) {
                res.setStatus(429); res.setContentType("application/json");
                res.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded; retry in a minute\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
