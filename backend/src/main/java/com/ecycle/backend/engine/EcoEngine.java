package com.ecycle.backend.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Gamification maths: points, levels, carbon equivalents, challenge progress and leaderboard ranking. Pure functions, no database.
 * Point rates themselves are configurable by admins in score_config (keys eco.*), see EcoService.
 */
public final class EcoEngine {
    private EcoEngine() {}

    // ---- levels: level n starts at 100 * (n-1)^2 lifetime points (L2=100, L3=400, L4=900, L5=1600 ...)
    private static final String[] LEVEL_NAMES = {"Seedling", "Sprout", "Sapling", "Young Tree", "Grove Keeper", "Forest Guardian", "Eco Champion", "Planet Hero"};
    public static int level(long lifetimePoints) { return 1 + (int) Math.floor(Math.sqrt(Math.max(0, lifetimePoints) / 100.0)); }
    public static long pointsForLevel(int level) { return level <= 1 ? 0 : 100L * (level - 1) * (level - 1); }
    public static String levelName(int level) { return LEVEL_NAMES[Math.min(LEVEL_NAMES.length, Math.max(1, level)) - 1]; }
    /** 0..1 progress from the current level's floor to the next level's floor. */
    public static double levelProgress(long lifetimePoints) {
        int l = level(lifetimePoints); long lo = pointsForLevel(l), hi = pointsForLevel(l + 1);
        return Math.min(1, Math.max(0, (lifetimePoints - lo) / (double) (hi - lo)));
    }

    // ---- points
    public static int pointsForWeight(double ratePerKg, double kg) { return kg <= 0 || ratePerKg <= 0 ? 0 : Math.max(1, (int) Math.round(ratePerKg * kg)); }

    // ---- carbon equivalents (ROUGH, widely quoted approximations; shown to users as estimates)
    public static final double KG_CO2_PER_TREE_YEAR = 21.0;       // mature tree absorbs roughly 21 kg CO2 per year
    public static final double KG_CO2_PER_CAR_KM = 0.25;          // average passenger car, approx. 250 g CO2 per km
    public static final double KG_CO2_PER_PHONE_CHARGE = 0.0082;  // approx. 8 g CO2 per smartphone charge
    public record Equivalents(double treeYears, double carKm, double phoneCharges) {}
    public static Equivalents equivalents(double carbonKg) {
        double c = Math.max(0, carbonKg);
        return new Equivalents(round2(c / KG_CO2_PER_TREE_YEAR), round2(c / KG_CO2_PER_CAR_KM), Math.round(c / KG_CO2_PER_PHONE_CHARGE));
    }

    // ---- challenges
    public static double challengeFraction(double progress, double target) { return target <= 0 ? 1 : Math.min(1, Math.max(0, progress / target)); }

    // ---- leaderboard (competition ranking: 1,2,2,4)
    public record Ranked(int rank, int index) {}
    public static List<Ranked> rank(List<Long> scoresDescending) {
        List<Ranked> out = new ArrayList<>(); int rank = 0;
        for (int i = 0; i < scoresDescending.size(); i++) {
            if (i == 0 || !scoresDescending.get(i).equals(scoresDescending.get(i - 1))) rank = i + 1;
            out.add(new Ranked(rank, i));
        }
        return out;
    }
    /** "Asha Kumar" -> "Asha K." so leaderboards do not expose full names. */
    public static String publicName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Eco member";
        String[] p = fullName.trim().split("\\s+");
        return p.length == 1 ? p[0] : p[0] + " " + Character.toUpperCase(p[p.length - 1].charAt(0)) + ".";
    }
    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
