package com.ecycle.backend.engine;

import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Ranks listings against a buyer requirement and explains every point awarded. Weights: category 30, budget 25, location 15, condition 10, use 10, components 5, pathway 5. */
public final class MatchingEngine {
    public record Req(Long categoryId, BigDecimal maxBudget, String city, ItemCondition minCondition, String intendedUse,
                      String requiredComponents, Pathway preferredPathway) {}
    public record Cand(Long id, Long categoryId, BigDecimal price, String city, ItemCondition condition, String text,
                       Pathway pathway, double circularScore) {}
    public record Match(Long listingId, double score, List<String> reasons) {}

    public static final double MIN_SCORE = 35;
    private static final Pattern SPLIT = Pattern.compile("[^a-z0-9]+");

    public List<Match> rank(Req req, Collection<Cand> cands) {
        List<Match> out = new ArrayList<>();
        Map<Long, Double> tie = new HashMap<>();
        for (Cand c : cands) {
            if (req.categoryId() != null && !req.categoryId().equals(c.categoryId())) continue; // category is a hard filter
            double s = 0; List<String> why = new ArrayList<>();
            if (req.categoryId() != null) { s += 30; why.add("Matches your category (+30)"); } else { s += 15; why.add("No category preference (+15)"); }
            if (req.maxBudget() == null) { s += 12.5; why.add("No budget set (+12.5)"); }
            else if (c.price().compareTo(req.maxBudget()) <= 0) { s += 25; why.add("Within budget " + req.maxBudget() + " (+25)"); }
            else if (c.price().compareTo(req.maxBudget().multiply(new BigDecimal("1.2"))) <= 0) { s += 10; why.add("Up to 20% over budget (+10)"); }
            else why.add("Over budget (+0)");
            if (isBlank(req.city())) { s += 7.5; why.add("No location preference (+7.5)"); }
            else if (req.city().equalsIgnoreCase(c.city())) { s += 15; why.add("Pickup in " + c.city() + " (+15)"); }
            else why.add("Different city: " + c.city() + " (+0)");
            if (req.minCondition() == null) { s += 5; why.add("No condition preference (+5)"); }
            else if (c.condition().ordinal() >= req.minCondition().ordinal()) { s += 10; why.add("Condition " + c.condition() + " meets minimum (+10)"); }
            else if (c.condition().ordinal() == req.minCondition().ordinal() - 1) { s += 4; why.add("One grade below preferred condition (+4)"); }
            else why.add("Condition below requirement (+0)");
            double use = overlap(req.intendedUse(), c.text());
            if (isBlank(req.intendedUse())) { s += 5; why.add("No intended use given (+5)"); }
            else { s += 10 * use; why.add(String.format(Locale.ROOT, "Intended-use keywords found: %.0f%% (+%.1f)", use * 100, 10 * use)); }
            double comp = overlap(req.requiredComponents(), c.text());
            if (isBlank(req.requiredComponents())) { s += 2.5; why.add("No required components given (+2.5)"); }
            else { s += 5 * comp; why.add(String.format(Locale.ROOT, "Required components mentioned: %.0f%% (+%.1f)", comp * 100, 5 * comp)); }
            if (req.preferredPathway() == null) { s += 2.5; why.add("No pathway preference (+2.5)"); }
            else if (req.preferredPathway() == c.pathway()) { s += 5; why.add("Processing pathway " + c.pathway() + " matches (+5)"); }
            else why.add("Pathway " + c.pathway() + " differs from preferred " + req.preferredPathway() + " (+0)");
            s = Math.round(s * 10.0) / 10.0;
            if (s >= MIN_SCORE) { out.add(new Match(c.id(), s, why)); tie.put(c.id(), c.circularScore()); }
        }
        out.sort(Comparator.comparingDouble(Match::score).reversed().thenComparing(m -> -tie.getOrDefault(m.listingId(), 0.0)));
        return out;
    }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static Set<String> tokens(String s) {
        Set<String> t = new HashSet<>();
        if (s != null) for (String w : SPLIT.split(s.toLowerCase(Locale.ROOT))) if (w.length() > 2) t.add(w);
        return t;
    }
    static double overlap(String needs, String haystack) {
        Set<String> n = tokens(needs); if (n.isEmpty()) return 0;
        Set<String> h = tokens(haystack); long hit = n.stream().filter(h::contains).count();
        return (double) hit / n.size();
    }
}
