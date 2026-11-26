package com.ecycle.backend.service;

import com.ecycle.backend.engine.CircularValueEngine;
import com.ecycle.backend.entity.Listing;
import com.ecycle.backend.entity.ProductCategory;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Adapts the pure CircularValueEngine to persisted listings using admin-configurable weights/thresholds. */
@Service @RequiredArgsConstructor
public class ScoringService {
    private final ScoreConfigService cfg; private final Lookup lookup;

    public CircularValueEngine.Result score(Listing l) {
        ProductCategory c = lookup.category(l.getCategoryId());
        return new CircularValueEngine(cfg.all()).evaluate(new CircularValueEngine.Input(l.getFunctionalStatus(), l.getPhysicalCondition(),
                l.getAgeMonths(), c.getRepairabilityBase(), c.getComponentRecoveryBase(), c.getLifeMonths(), l.getHazardLevel()));
    }
    /** Recomputes and stores all factor scores, the overall score, the recommended pathway and the explanation. */
    public void apply(Listing l) {
        var r = score(l);
        l.setScoreFunction(BigDecimal.valueOf(r.functionality())); l.setScoreCondition(BigDecimal.valueOf(r.condition()));
        l.setScoreRepair(BigDecimal.valueOf(r.repairability())); l.setScoreReuse(BigDecimal.valueOf(r.reusability()));
        l.setScoreComponent(BigDecimal.valueOf(r.componentRecovery())); l.setScoreLife(BigDecimal.valueOf(r.remainingLife()));
        l.setCircularScore(BigDecimal.valueOf(r.overall())); l.setRecommendedPathway(r.pathway()); l.setScoreExplanation(r.explanation());
    }
}
