package com.ecycle.backend.engine;

import com.ecycle.shared.Enums.HazardLevel;
import java.util.*;

/**
 * Questionnaire-based hazard assessment. This is a rules engine over answers a human gives; it is NOT a hardware diagnostic or
 * computer-vision inspection and its output must be described that way in the UI.
 */
public final class HazardEngine {
    public record Rule(String code, String description, int points, boolean blocksShipping, boolean requiresSanitization,
                       String handling, String specialistType) {}
    public record Result(int totalPoints, HazardLevel level, boolean blocked, boolean requiresSanitization,
                         List<String> findings, List<String> handling, Set<String> specialists) {}

    private final int moderateFrom, highFrom, criticalFrom;
    public HazardEngine() { this(15, 35, 60); }
    public HazardEngine(int moderateFrom, int highFrom, int criticalFrom) {
        this.moderateFrom = moderateFrom; this.highFrom = highFrom; this.criticalFrom = criticalFrom;
    }

    public Result assess(Collection<Rule> rules, Map<String, Boolean> answers) {
        Map<String, Boolean> a = answers == null ? Map.of() : answers;
        int total = 0; boolean blocked = false, sanitize = false;
        List<String> findings = new ArrayList<>(), handling = new ArrayList<>(); Set<String> specialists = new LinkedHashSet<>();
        for (Rule r : rules) {
            if (!Boolean.TRUE.equals(a.get(r.code()))) continue;
            total += r.points();
            blocked |= r.blocksShipping(); sanitize |= r.requiresSanitization();
            findings.add(r.description() + " (+" + r.points() + " risk points" + (r.blocksShipping() ? ", blocks normal shipping" : "") + ")");
            handling.add(r.code() + ": " + r.handling());
            if (r.specialistType() != null && !r.specialistType().isBlank()) specialists.add(r.specialistType());
        }
        HazardLevel level = total >= criticalFrom ? HazardLevel.CRITICAL : total >= highFrom ? HazardLevel.HIGH
                : total >= moderateFrom ? HazardLevel.MODERATE : HazardLevel.LOW;
        if (blocked && level.ordinal() < HazardLevel.HIGH.ordinal()) level = HazardLevel.HIGH;
        if (findings.isEmpty()) findings.add("No hazards reported in the questionnaire.");
        return new Result(total, level, blocked, sanitize, findings, handling, specialists);
    }
}
