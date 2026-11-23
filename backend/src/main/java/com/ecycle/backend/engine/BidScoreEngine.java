package com.ecycle.backend.engine;

import java.util.*;

/** Circular Bid Score (0-100): weighted blend of price, buyer verification, recovery capability and environmental performance. */
public final class BidScoreEngine {
    public record Input(double price, double referencePrice, boolean verified, int recoveryCapability, int environmentalPerformance) {}
    public static final Map<String, Double> DEFAULTS = Map.of("bid.w.price", 0.40, "bid.w.verification", 0.20,
            "bid.w.recovery", 0.20, "bid.w.environment", 0.20);
    private final Map<String, Double> cfg = new HashMap<>(DEFAULTS);

    public BidScoreEngine(Map<String, Double> overrides) { if (overrides != null) cfg.putAll(overrides); }

    /** referencePrice is the best known price (max of the listing's expected price and the highest bid), so a price equal to it scores 100. */
    public double score(Input in) {
        double price = in.referencePrice() <= 0 ? 100 : Math.min(100, 100.0 * in.price() / in.referencePrice());
        double ver = in.verified() ? 100 : 0;
        double rec = Math.max(0, Math.min(100, in.recoveryCapability()));
        double env = Math.max(0, Math.min(100, in.environmentalPerformance()));
        double w = cfg.get("bid.w.price") + cfg.get("bid.w.verification") + cfg.get("bid.w.recovery") + cfg.get("bid.w.environment");
        double s = (cfg.get("bid.w.price") * price + cfg.get("bid.w.verification") * ver + cfg.get("bid.w.recovery") * rec + cfg.get("bid.w.environment") * env) / w;
        return Math.round(s * 10.0) / 10.0;
    }
}
