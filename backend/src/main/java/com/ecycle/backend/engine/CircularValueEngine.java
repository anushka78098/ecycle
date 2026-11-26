package com.ecycle.backend.engine;

import com.ecycle.shared.Enums.*;
import java.util.*;

/**
 * Rule-based Circular Value Score (0-100). Pure Java, no framework dependencies, so it can be swapped for an ML model
 * later behind the same Input/Result contract. Formulas are documented in docs/FORMULAS.md.
 */
public final class CircularValueEngine {
    public record Input(FunctionalStatus functional, ItemCondition condition, int ageMonths,
                        int categoryRepairability, int categoryComponentRecovery, int categoryLifeMonths, HazardLevel hazard) {}
    public record Result(double overall, double functionality, double condition, double repairability, double reusability,
                         double componentRecovery, double remainingLife, Pathway pathway, String explanation) {}

    public static final Map<String, Double> DEFAULTS = Map.ofEntries(
            Map.entry("w.functionality", 0.25), Map.entry("w.condition", 0.20), Map.entry("w.repairability", 0.15),
            Map.entry("w.reusability", 0.15), Map.entry("w.component", 0.10), Map.entry("w.life", 0.15),
            Map.entry("t.reuse.overall", 75.0), Map.entry("t.reuse.functionality", 85.0),
            Map.entry("t.refurbish.overall", 60.0), Map.entry("t.refurbish.condition", 50.0), Map.entry("t.refurbish.repair", 50.0),
            Map.entry("t.repair.overall", 45.0), Map.entry("t.repair.repair", 55.0), Map.entry("t.recycle.component", 25.0));

    private final Map<String, Double> cfg = new HashMap<>(DEFAULTS);

    public CircularValueEngine(Map<String, Double> overrides) { if (overrides != null) cfg.putAll(overrides); }

    static double functionalityScore(FunctionalStatus f) {
        return switch (f) { case FULLY_WORKING -> 100; case PARTIALLY_WORKING -> 55; case NOT_WORKING -> 15; };
    }
    static double conditionScore(ItemCondition c) {
        return switch (c) { case LIKE_NEW -> 100; case GOOD -> 80; case FAIR -> 55; case POOR -> 30; case SCRAP -> 10; };
    }
    private static double r1(double v) { return Math.round(Math.max(0, Math.min(100, v)) * 10.0) / 10.0; }

    public Result evaluate(Input in) {
        double func = functionalityScore(in.functional());
        double cond = conditionScore(in.condition());
        double condMul = 0.5 + 0.5 * cond / 100.0;
        int life = Math.max(1, in.categoryLifeMonths());
        double remaining = Math.max(0, 1.0 - (double) Math.max(0, in.ageMonths()) / life) * 100.0 * condMul;
        double repair = in.categoryRepairability() * condMul;
        double reuse = (0.6 * func + 0.4 * cond) * (0.5 + 0.5 * remaining / 100.0);
        double component = in.categoryComponentRecovery() * (0.6 + 0.4 * cond / 100.0);

        double wSum = cfg.get("w.functionality") + cfg.get("w.condition") + cfg.get("w.repairability")
                + cfg.get("w.reusability") + cfg.get("w.component") + cfg.get("w.life");
        double overall = (cfg.get("w.functionality") * func + cfg.get("w.condition") * cond + cfg.get("w.repairability") * repair
                + cfg.get("w.reusability") * reuse + cfg.get("w.component") * component + cfg.get("w.life") * remaining) / wSum;

        String rule; Pathway p;
        boolean hazardous = in.hazard() == HazardLevel.HIGH || in.hazard() == HazardLevel.CRITICAL;
        if (hazardous) {
            p = component >= cfg.get("t.recycle.component") ? Pathway.RECYCLE : Pathway.DISPOSE;
            rule = "Hazard level " + in.hazard() + " overrides normal routing: only specialist recycling/disposal is safe.";
        } else if (overall >= cfg.get("t.reuse.overall") && func >= cfg.get("t.reuse.functionality")) {
            p = Pathway.REUSE; rule = "Overall >= " + cfg.get("t.reuse.overall") + " and functionality >= " + cfg.get("t.reuse.functionality") + ": suitable for direct reuse.";
        } else if (overall >= cfg.get("t.refurbish.overall") && cond >= cfg.get("t.refurbish.condition") && repair >= cfg.get("t.refurbish.repair")) {
            p = Pathway.REFURBISH; rule = "Overall >= " + cfg.get("t.refurbish.overall") + ", condition and repairability above refurbish thresholds: refurbishment restores most value.";
        } else if (overall >= cfg.get("t.repair.overall") && repair >= cfg.get("t.repair.repair")) {
            p = Pathway.REPAIR; rule = "Overall >= " + cfg.get("t.repair.overall") + " and repairability >= " + cfg.get("t.repair.repair") + ": a repair is economical.";
        } else if (component >= cfg.get("t.recycle.component")) {
            p = Pathway.RECYCLE; rule = "Low reuse value but component recovery >= " + cfg.get("t.recycle.component") + ": recycle for materials and parts.";
        } else {
            p = Pathway.DISPOSE; rule = "Neither reuse, repair nor material recovery is worthwhile: controlled disposal.";
        }
        Map<String, Double> f = new LinkedHashMap<>();
        f.put("functionality", func); f.put("condition", cond); f.put("repairability", repair);
        f.put("reusability", reuse); f.put("component recovery", component); f.put("remaining life", remaining);
        var best = Collections.max(f.entrySet(), Map.Entry.comparingByValue());
        var worst = Collections.min(f.entrySet(), Map.Entry.comparingByValue());
        String expl = String.format(Locale.ROOT, "Score %.1f/100. Strongest factor: %s (%.0f). Weakest: %s (%.0f). %s",
                overall, best.getKey(), best.getValue(), worst.getKey(), worst.getValue(), rule);
        return new Result(r1(overall), r1(func), r1(cond), r1(repair), r1(reuse), r1(component), r1(remaining), p, expl);
    }
}
