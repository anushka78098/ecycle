package com.ecycle.backend.service;

import com.ecycle.backend.engine.EnvironmentalCalculator;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.Pathway;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class EnvironmentalService {
    static final String NOTE = "Figures are ESTIMATES computed from configurable conversion factors and category average weights. "
            + "Records marked measured used a handler-measured weight; carbon and materials are still factor-based. Each record stores its inputs and methodology.";
    private final EnvironmentalImpactRepository impacts; private final EnvironmentalFactorRepository factors; private final ProductCategoryRepository categories;
    private final PurchaseOrderRepository orders; private final AuditService audit;
    private final EnvironmentalCalculator calc = new EnvironmentalCalculator();

    /** Counts a product's diverted weight once: later outcomes for the same listing only add lifecycle history. */
    @Transactional
    public void compute(Listing l, Long orderId, Pathway pathway, Long actorId, BigDecimal measuredWeightKg) {
        if (!impacts.findByListingIdOrderByCreatedAtDesc(l.getId()).isEmpty()) return;
        EnvironmentalFactor f = factors.findByCategoryId(l.getCategoryId()).orElseThrow(() -> ApiException.conflict("No environmental factors configured for this category"));
        boolean measured = measuredWeightKg != null;
        BigDecimal unit = measured ? measuredWeightKg : l.getWeightKg();
        var res = calc.calculate(unit.doubleValue(), l.getQuantity(), pathway, new EnvironmentalCalculator.Factors(f.getCarbonReusePerKg().doubleValue(),
                f.getCarbonRepairPerKg().doubleValue(), f.getCarbonRefurbishPerKg().doubleValue(), f.getCarbonRecyclePerKg().doubleValue(), f.getMetalsRecoveryPct().doubleValue()), measured);
        EnvironmentalImpact i = new EnvironmentalImpact();
        i.setListingId(l.getId()); i.setOrderId(orderId); i.setSellerId(l.getSellerId());
        if (orderId != null) orders.findById(orderId).ifPresent(o -> i.setBuyerId(o.getBuyerId()));
        i.setPathway(pathway); i.setQuantity(l.getQuantity()); i.setWeightKg(unit);
        i.setEwasteDivertedKg(BigDecimal.valueOf(res.ewasteDivertedKg())); i.setCarbonAvoidedKg(BigDecimal.valueOf(res.carbonAvoidedKg()));
        i.setMaterialsRecoveredKg(BigDecimal.valueOf(res.materialsRecoveredKg())); i.setMeasured(measured);
        i.setMethodology(res.methodology()); i.setInputs(res.inputs()); i.setRecordedBy(actorId);
        impacts.save(i);
        audit.log(actorId, "IMPACT_RECORDED", "environmental_impact", i.getId(), res.inputs());
    }

    @Transactional(readOnly = true)
    public ImpactDto summary(String scope, Long userId) {
        List<Object[]> rows = switch (scope) { case "seller" -> impacts.summaryBySeller(userId); case "buyer" -> impacts.summaryByBuyer(userId); default -> impacts.summaryAll(); };
        double ew = 0, co = 0, mat = 0; long reuse = 0, repair = 0, refurb = 0, recycle = 0, dispose = 0, total = 0, measured = 0;
        for (Object[] r : rows) {
            Pathway p = (Pathway) r[0]; long n = ((Number) r[1]).longValue();
            ew += ((Number) r[2]).doubleValue(); co += ((Number) r[3]).doubleValue(); mat += ((Number) r[4]).doubleValue();
            measured += ((Number) r[5]).longValue(); total += n;
            switch (p) { case REUSE -> reuse += n; case REPAIR -> repair += n; case REFURBISH -> refurb += n; case RECYCLE -> recycle += n; case DISPOSE -> dispose += n; }
        }
        return new ImpactDto(round(ew), round(co), round(mat), reuse, repair, refurb, recycle, dispose, total - measured, measured, NOTE);
    }
    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }

    // ---- admin-configurable factors
    @Transactional(readOnly = true)
    public List<EnvFactorDto> listFactors() {
        Map<Long, String> names = new HashMap<>(); categories.findAll().forEach(c -> names.put(c.getId(), c.getName()));
        return factors.findAll().stream().map(f -> toDto(f, names.get(f.getCategoryId()))).sorted(Comparator.comparing(EnvFactorDto::categoryName)).toList();
    }
    @Transactional
    public EnvFactorDto upsert(Long adminId, EnvFactorRequest r) {
        ProductCategory c = categories.findById(r.categoryId()).orElseThrow(() -> ApiException.notFound("Category not found"));
        EnvironmentalFactor f = factors.findByCategoryId(c.getId()).orElseGet(EnvironmentalFactor::new);
        f.setCategoryId(c.getId()); f.setCarbonReusePerKg(BigDecimal.valueOf(r.carbonReusePerKg())); f.setCarbonRepairPerKg(BigDecimal.valueOf(r.carbonRepairPerKg()));
        f.setCarbonRefurbishPerKg(BigDecimal.valueOf(r.carbonRefurbishPerKg())); f.setCarbonRecyclePerKg(BigDecimal.valueOf(r.carbonRecyclePerKg()));
        f.setMetalsRecoveryPct(BigDecimal.valueOf(r.metalsRecoveryPct())); f.setSourceNote(r.sourceNote()); f.setUpdatedBy(adminId);
        factors.save(f);
        audit.log(adminId, "ENV_FACTOR_UPDATED", "environmental_factors", f.getId(), "category=" + c.getName());
        return toDto(f, c.getName());
    }
    private static EnvFactorDto toDto(EnvironmentalFactor f, String name) {
        return new EnvFactorDto(f.getId(), f.getCategoryId(), name, f.getCarbonReusePerKg().doubleValue(), f.getCarbonRepairPerKg().doubleValue(),
                f.getCarbonRefurbishPerKg().doubleValue(), f.getCarbonRecyclePerKg().doubleValue(), f.getMetalsRecoveryPct().doubleValue(), f.getSourceNote(), f.getUpdatedAt());
    }
}
