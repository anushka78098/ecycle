package com.ecycle.frontend.util;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.concurrent.Task;

/** Runs blocking work (HTTP) on a daemon pool and delivers results on the JavaFX thread, so the UI never blocks. */
public final class Async {
    private Async() {}
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> { Thread t = new Thread(r, "ecycle-io"); t.setDaemon(true); return t; });

    public static <T> void run(Callable<T> work, Consumer<T> ok) { run(work, ok, Alerts::error); }

    public static <T> void run(Callable<T> work, Consumer<T> ok, Consumer<Throwable> err) {
        Task<T> t = new Task<>() { @Override protected T call() throws Exception { return work.call(); } };
        t.setOnSucceeded(e -> ok.accept(t.getValue()));
        t.setOnFailed(e -> err.accept(t.getException()));
        POOL.submit(t);
    }
}
