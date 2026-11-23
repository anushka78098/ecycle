package com.ecycle.frontend.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal STOMP 1.2 client over java.net.http.WebSocket (no extra dependencies). Connects to /ws with the JWT in the CONNECT frame, subscribes to /user/queue/events and
 * dispatches LiveEvent JSON ({type: PICKUP|NOTIFICATION|CHAT, payload, at}) to listeners on the JavaFX thread. Reconnects with backoff; screens still work through REST if it is down.
 */
public final class LiveClient {
    private static final Logger log = LoggerFactory.getLogger(LiveClient.class);
    private static final char NUL = '\0';
    private static final List<Consumer<JsonNode>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "ecycle-live"); t.setDaemon(true); return t; });
    private static volatile WebSocket socket; private static volatile boolean wanted; private static volatile int attempt; private static volatile boolean connected;

    private LiveClient() {}
    public static boolean connected() { return connected; }
    public static Runnable listen(Consumer<JsonNode> l) { LISTENERS.add(l); return () -> LISTENERS.remove(l); }

    public static synchronized void start() { if (wanted || !Session.active()) return; wanted = true; attempt = 0; connect(); }
    public static synchronized void stop() {
        wanted = false; connected = false; WebSocket s = socket; socket = null;
        if (s != null) { try { s.sendText("DISCONNECT\n\n" + NUL, true); } catch (RuntimeException ignored) { } s.sendClose(WebSocket.NORMAL_CLOSURE, "bye"); }
    }

    static String wsUrl() { return ApiClient.BASE.replaceFirst("^http", "ws") + "/ws"; }

    private static void connect() {
        if (!wanted) return;
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().newWebSocketBuilder().buildAsync(URI.create(wsUrl()), new Listener()).whenComplete((ws, err) -> {
            if (err != null) { log.debug("Live channel unavailable: {}", err.getMessage()); retry(); return; }
            socket = ws;
            ws.sendText("CONNECT\naccept-version:1.2\nheart-beat:0,0\nAuthorization:Bearer " + Session.token + "\n\n" + NUL, true);
        });
    }
    private static void retry() {
        connected = false;
        if (!wanted) return;
        long delay = Math.min(60, 2L << Math.min(attempt++, 5));
        TIMER.schedule(LiveClient::connect, delay, TimeUnit.SECONDS);
    }

    private static final class Listener implements WebSocket.Listener {
        private final StringBuilder buf = new StringBuilder();
        @Override public void onOpen(WebSocket ws) { ws.request(1); }
        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buf.append(data);
            if (last) { String all = buf.toString(); buf.setLength(0); for (String frame : all.split(String.valueOf(NUL))) if (!frame.isBlank()) handle(ws, frame); }
            ws.request(1); return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket ws, int code, String reason) { retry(); return null; }
        @Override public void onError(WebSocket ws, Throwable e) { log.debug("Live channel error: {}", e.getMessage()); retry(); }
    }

    /** Parses one STOMP frame (command line, headers, blank line, body). */
    static void handle(WebSocket ws, String frame) {
        String f = frame.startsWith("\n") ? frame.substring(1) : frame;
        int headEnd = f.indexOf("\n\n"); String head = headEnd < 0 ? f : f.substring(0, headEnd), body = headEnd < 0 ? "" : f.substring(headEnd + 2);
        String cmd = head.lines().findFirst().orElse("").trim();
        switch (cmd) {
            case "CONNECTED" -> { connected = true; attempt = 0; ws.sendText("SUBSCRIBE\nid:sub-0\ndestination:/user/queue/events\n\n" + NUL, true); }
            case "MESSAGE" -> {
                try { JsonNode n = ApiClient.mapper().readTree(body); Platform.runLater(() -> LISTENERS.forEach(l -> { try { l.accept(n); } catch (RuntimeException e) { log.warn("Live listener failed", e); } })); }
                catch (Exception e) { log.debug("Bad live payload: {}", e.getMessage()); }
            }
            case "ERROR" -> { log.debug("STOMP error: {}", head); connected = false; }
            default -> { }
        }
    }
}
