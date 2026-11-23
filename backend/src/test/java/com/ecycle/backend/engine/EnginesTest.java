package com.ecycle.backend.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pure-Java business rule engines: Circular Value Score, Circular Bid Score, hazard risk, environmental impact, matching. */
class EnginesTest {
    // ---- Circular Value Score
    private final CircularValueEngine cv = new CircularValueEngine(null);
    private CircularValueEngine.Result eval(FunctionalStatus f, ItemCondition c, int age, int rep, int comp, int life, HazardLevel h) {
        return cv.evaluate(new CircularValueEngine.Input(f, c, age, rep, comp, life, h));
    }
    @Test void nearlyNewWorkingLaptopIsReuse() {
        var r = eval(FunctionalStatus.FULLY_WORKING, ItemCondition.LIKE_NEW, 6, 70, 70, 72, HazardLevel.LOW);
        assertEquals(Pathway.REUSE, r.pathway()); assertTrue(r.overall() > 75 && r.overall() <= 100);
        assertTrue(r.explanation().contains("direct reuse"));
    }
    @Test void partiallyWorkingGetsRefurbishOrRepair() {
        var r = eval(FunctionalStatus.PARTIALLY_WORKING, ItemCondition.GOOD, 30, 70, 70, 72, HazardLevel.LOW);
        assertTrue(r.pathway() == Pathway.REFURBISH || r.pathway() == Pathway.REPAIR);
    }
    @Test void hazardOverridesToSpecialistRecycling() {
        var r = eval(FunctionalStatus.FULLY_WORKING, ItemCondition.GOOD, 12, 55, 45, 48, HazardLevel.CRITICAL);
        assertEquals(Pathway.RECYCLE, r.pathway()); assertTrue(r.explanation().contains("overrides"));
    }
    @Test void scoresAreAlwaysBounded() {
        for (FunctionalStatus f : FunctionalStatus.values()) for (ItemCondition c : ItemCondition.values()) for (int age : new int[]{0, 500}) {
            var r = eval(f, c, age, 100, 100, 12, HazardLevel.LOW);
            for (double v : new double[]{r.overall(), r.functionality(), r.condition(), r.repairability(), r.reusability(), r.componentRecovery(), r.remainingLife()}) assertTrue(v >= 0 && v <= 100);
        }
    }
    @Test void configuredThresholdsChangeThePathway() {
        var strict = new CircularValueEngine(Map.of("t.reuse.overall", 99.0, "t.refurbish.overall", 99.0, "t.repair.overall", 99.0));
        var r = strict.evaluate(new CircularValueEngine.Input(FunctionalStatus.FULLY_WORKING, ItemCondition.LIKE_NEW, 6, 70, 70, 72, HazardLevel.LOW));
        assertEquals(Pathway.RECYCLE, r.pathway());
    }
    // ---- Circular Bid Score
    private final BidScoreEngine bs = new BidScoreEngine(null);
    @Test void perfectBidScores100AndWeakBidLess() {
        assertEquals(100.0, bs.score(new BidScoreEngine.Input(1000, 1000, true, 100, 100)));
        assertTrue(bs.score(new BidScoreEngine.Input(1000, 1000, true, 80, 80)) > bs.score(new BidScoreEngine.Input(600, 1000, false, 20, 20)));
    }
    @Test void highestPriceNeedNotHaveHighestCircularBidScore() {
        double greener = bs.score(new BidScoreEngine.Input(700, 1000, true, 100, 100));
        double richer = bs.score(new BidScoreEngine.Input(1000, 1000, true, 10, 10));
        assertTrue(greener > richer);
    }
    @Test void bidScoreClampsOutOfRangeInputs() { assertTrue(bs.score(new BidScoreEngine.Input(5000, 1000, true, 500, -5)) <= 100); }
    // ---- Hazard
    private final HazardEngine he = new HazardEngine();
    private final List<HazardEngine.Rule> rules = List.of(new HazardEngine.Rule("BATTERY_SWELLING", "swollen", 40, true, false, "isolate", "Battery recycler"),
            new HazardEngine.Rule("STORAGE_DEVICE_PRESENT", "storage", 5, false, true, "sanitize", "DSP"), new HazardEngine.Rule("PHYSICAL_DAMAGE", "damage", 10, false, false, "pad", null));
    @Test void swollenBatteryBlocksShippingAndRoutesToSpecialist() {
        var r = he.assess(rules, Map.of("BATTERY_SWELLING", true, "STORAGE_DEVICE_PRESENT", true));
        assertTrue(r.blocked()); assertTrue(r.requiresSanitization()); assertEquals(HazardLevel.HIGH, r.level()); assertTrue(r.specialists().contains("Battery recycler"));
        assertEquals(2, r.findings().size()); assertEquals(2, r.handling().size());
    }
    @Test void noAnswersMeansLowRisk() { var r = he.assess(rules, Map.of()); assertEquals(HazardLevel.LOW, r.level()); assertFalse(r.blocked()); assertEquals(0, r.totalPoints()); }
    @Test void falseAnswersAreIgnoredAndNullIsSafe() { assertEquals(0, he.assess(rules, Map.of("BATTERY_SWELLING", false)).totalPoints()); assertEquals(HazardLevel.LOW, he.assess(rules, null).level()); }
    @Test void pointsAccumulateIntoCritical() {
        var many = List.of(new HazardEngine.Rule("A", "a", 30, false, false, "h", null), new HazardEngine.Rule("B", "b", 35, false, false, "h", null));
        assertEquals(HazardLevel.CRITICAL, he.assess(many, Map.of("A", true, "B", true)).level());
    }
    // ---- Environmental impact
    private final EnvironmentalCalculator env = new EnvironmentalCalculator();
    private final EnvironmentalCalculator.Factors f = new EnvironmentalCalculator.Factors(35, 28, 30, 9, 22);
    @Test void recycleImpactIsComputedFromFactors() {
        var r = env.calculate(2.0, 3, Pathway.RECYCLE, f, false);
        assertEquals(6.0, r.ewasteDivertedKg()); assertEquals(54.0, r.carbonAvoidedKg()); assertEquals(1.32, r.materialsRecoveredKg(), 1e-9);
        assertTrue(r.methodology().contains("ESTIMATES")); assertTrue(r.inputs().contains("\"weightMeasured\":false"));
    }
    @Test void measuredWeightIsLabelledAsMeasured() { assertTrue(env.calculate(2.0, 1, Pathway.REUSE, f, true).methodology().contains("MEASURED")); }
    @Test void disposalAvoidsNothingAndBadInputIsRejected() {
        assertEquals(0, env.calculate(2, 1, Pathway.DISPOSE, f, false).carbonAvoidedKg());
        assertThrows(IllegalArgumentException.class, () -> env.calculate(0, 1, Pathway.REUSE, f, false));
        assertThrows(IllegalArgumentException.class, () -> env.calculate(1, 0, Pathway.REUSE, f, false));
    }
    // ---- Matching
    private final MatchingEngine me = new MatchingEngine();
    @Test void matchesAreRankedAndExplained() {
        var req = new MatchingEngine.Req(2L, new BigDecimal("30000"), "Patiala", ItemCondition.GOOD, "office work student", "ssd ram", Pathway.REFURBISH);
        var res = me.rank(req, List.of(
                new MatchingEngine.Cand(1L, 2L, new BigDecimal("25000"), "Patiala", ItemCondition.GOOD, "Dell laptop for office work with 8GB RAM and SSD", Pathway.REFURBISH, 80),
                new MatchingEngine.Cand(2L, 2L, new BigDecimal("50000"), "Delhi", ItemCondition.POOR, "Old laptop", Pathway.RECYCLE, 30),
                new MatchingEngine.Cand(3L, 5L, new BigDecimal("1000"), "Patiala", ItemCondition.GOOD, "Monitor", Pathway.REUSE, 90)));
        assertFalse(res.isEmpty()); assertEquals(1L, res.get(0).listingId()); assertTrue(res.get(0).reasons().size() >= 6);
        assertTrue(res.stream().noneMatch(m -> m.listingId() == 3L), "other categories are hard-filtered");
        for (int i = 1; i < res.size(); i++) assertTrue(res.get(i - 1).score() >= res.get(i).score());
    }
    @Test void overBudgetWrongCityListingScoresLower() {
        var req = new MatchingEngine.Req(null, new BigDecimal("1000"), "Goa", null, null, null, null);
        var res = me.rank(req, List.of(new MatchingEngine.Cand(1L, 1L, new BigDecimal("900"), "Goa", ItemCondition.FAIR, "x", Pathway.REUSE, 1), new MatchingEngine.Cand(2L, 1L, new BigDecimal("9000"), "Pune", ItemCondition.FAIR, "x", Pathway.REUSE, 1)));
        assertEquals(1L, res.get(0).listingId());
    }
}
