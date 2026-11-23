package com.ecycle.backend.engine;

import com.ecycle.shared.Enums.Pathway;
import java.util.Locale;

/** Environmental impact ESTIMATOR using configurable conversion factors. Results are estimates unless flagged measured. */
public final class EnvironmentalCalculator {
    public record Factors(double carbonReusePerKg, double carbonRepairPerKg, double carbonRefurbishPerKg,
                          double carbonRecyclePerKg, double metalsRecoveryPct) {}
    public record Result(double weightKgTotal, double ewasteDivertedKg, double carbonAvoidedKg, double materialsRecoveredKg,
                         String methodology, String inputs) {}

    public Result calculate(double unitWeightKg, int quantity, Pathway pathway, Factors f, boolean measuredWeight) {
        if (unitWeightKg <= 0 || quantity <= 0) throw new IllegalArgumentException("weight and quantity must be positive");
        double total = unitWeightKg * quantity;
        double diverted = pathway == Pathway.DISPOSE ? 0 : total;
        double perKg = switch (pathway) {
            case REUSE -> f.carbonReusePerKg(); case REPAIR -> f.carbonRepairPerKg();
            case REFURBISH -> f.carbonRefurbishPerKg(); case RECYCLE -> f.carbonRecyclePerKg(); case DISPOSE -> 0;
        };
        double carbon = diverted * perKg;
        double materials = pathway == Pathway.RECYCLE ? diverted * f.metalsRecoveryPct() / 100.0 : 0;
        String method = String.format(Locale.ROOT,
                "ewaste_diverted = weight x quantity (0 for DISPOSE); carbon_avoided = ewaste_diverted x factor[%s] (%.3f kg CO2e/kg); "
                        + "materials_recovered = ewaste_diverted x metals_recovery_pct (%.1f%%) for RECYCLE only. %s",
                pathway, perKg, f.metalsRecoveryPct(), measuredWeight ? "Weight was MEASURED by a verified handler; carbon and materials remain factor-based estimates." : "All values are ESTIMATES from category average weights/factors.");
        String inputs = String.format(Locale.ROOT, "{\"unitWeightKg\":%.3f,\"quantity\":%d,\"pathway\":\"%s\",\"factorPerKg\":%.3f,\"metalsRecoveryPct\":%.2f,\"weightMeasured\":%s}",
                unitWeightKg, quantity, pathway, perKg, f.metalsRecoveryPct(), measuredWeight);
        return new Result(round(total), round(diverted), round(carbon), round(materials), method, inputs);
    }
    private static double round(double v) { return Math.round(v * 1000.0) / 1000.0; }
}
