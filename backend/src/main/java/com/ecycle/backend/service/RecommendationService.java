package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.engine.MatchingEngine;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Feature 3: smart buyer-seller matching. Ranks live listings against saved requirements (or, failing that, wishlist behaviour) and explains each score. */
@Service @RequiredArgsConstructor
public class RecommendationService {
    private final BuyerRequirementRepository requirements; private final ListingRepository listings; private final WishlistRepository wishlist;
    private final ProductCategoryRepository categories; private final ListingMapper mapper; private final Lookup lookup; private final AuditService audit;
    private final MatchingEngine engine = new MatchingEngine();

    @Transactional
    public RequirementDto save(AuthUser me, Long id, RequirementRequest r) {
        lookup.requireRole(me, Role.BUYER);
        BuyerRequirement b = id == null ? new BuyerRequirement() : requirements.findById(id).filter(x -> x.getBuyerId().equals(me.id())).orElseThrow(() -> ApiException.notFound("Requirement not found"));
        if (r.categoryId() != null) lookup.category(r.categoryId());
        b.setBuyerId(me.id()); b.setCategoryId(r.categoryId()); b.setMaxBudget(r.maxBudget()); b.setCity(r.city()); b.setMinCondition(r.minCondition());
        b.setIntendedUse(r.intendedUse()); b.setRequiredComponents(r.requiredComponents()); b.setPreferredPathway(r.preferredPathway()); b.setActive(r.active());
        requirements.save(b);
        audit.log(me.id(), id == null ? "REQUIREMENT_CREATED" : "REQUIREMENT_UPDATED", "buyer_requirements", b.getId(), null);
        return dto(b);
    }
    @Transactional
    public void delete(AuthUser me, Long id) {
        BuyerRequirement b = requirements.findById(id).filter(x -> x.getBuyerId().equals(me.id())).orElseThrow(() -> ApiException.notFound("Requirement not found"));
        requirements.delete(b);
    }
    @Transactional(readOnly = true)
    public List<RequirementDto> mine(AuthUser me) { return requirements.findByBuyerIdOrderByCreatedAtDesc(me.id()).stream().map(this::dto).toList(); }

    @Transactional(readOnly = true)
    public List<RecommendationDto> recommend(AuthUser me, int limit) {
        List<Listing> live = listings.findByStatus(ListingStatus.APPROVED).stream().filter(l -> !l.getSellerId().equals(me.id())).toList();
        if (live.isEmpty()) return List.of();
        Map<Long, String> catName = categories.findAll().stream().collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName));
        List<MatchingEngine.Cand> cands = live.stream().map(l -> new MatchingEngine.Cand(l.getId(), l.getCategoryId(), l.getExpectedPrice(), l.getPickupCity(), l.getPhysicalCondition(),
                String.join(" ", nz(l.getName()), nz(l.getBrand()), nz(l.getModel()), nz(l.getDescription()), catName.getOrDefault(l.getCategoryId(), "")),
                l.getPathway() != null ? l.getPathway() : l.getRecommendedPathway(), l.getCircularScore() == null ? 0 : l.getCircularScore().doubleValue())).toList();

        Map<Long, MatchingEngine.Match> best = new HashMap<>(); Map<Long, String> basis = new HashMap<>();
        List<BuyerRequirement> active = requirements.findByBuyerIdAndActiveTrue(me.id());
        for (BuyerRequirement b : active) {
            var req = new MatchingEngine.Req(b.getCategoryId(), b.getMaxBudget(), b.getCity(), b.getMinCondition(), b.getIntendedUse(), b.getRequiredComponents(), b.getPreferredPathway());
            for (var m : engine.rank(req, cands)) if (!best.containsKey(m.listingId()) || best.get(m.listingId()).score() < m.score()) {
                best.put(m.listingId(), m); basis.put(m.listingId(), "Your requirement" + (b.getCategoryId() == null ? "" : " for " + catName.get(b.getCategoryId())));
            }
        }
        if (active.isEmpty()) {   // fall back to wishlist behaviour: most-wishlisted category and the average wishlisted price
            List<Listing> wl = wishlist.findByUserIdOrderByCreatedAtDesc(me.id()).stream().map(w -> listings.findById(w.getListingId()).orElse(null)).filter(Objects::nonNull).toList();
            if (!wl.isEmpty()) {
                Long cat = wl.stream().collect(Collectors.groupingBy(Listing::getCategoryId, Collectors.counting())).entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
                BigDecimal avg = wl.stream().map(Listing::getExpectedPrice).reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(wl.size()), 2, java.math.RoundingMode.HALF_UP);
                var req = new MatchingEngine.Req(cat, avg.multiply(new BigDecimal("1.2")), null, null, null, null, null);
                for (var m : engine.rank(req, cands)) if (wl.stream().noneMatch(w -> w.getId().equals(m.listingId()))) { best.put(m.listingId(), m); basis.put(m.listingId(), "Based on your wishlist"); }
            }
        }
        List<MatchingEngine.Match> ranked = new ArrayList<>(best.values());
        ranked.sort(Comparator.comparingDouble(MatchingEngine.Match::score).reversed());
        Map<Long, Listing> byId = live.stream().collect(Collectors.toMap(Listing::getId, l -> l));
        List<RecommendationDto> out = new ArrayList<>();
        List<ListingDto> dtos = mapper.toDtos(ranked.stream().limit(limit).map(m -> byId.get(m.listingId())).toList());
        for (int i = 0; i < dtos.size(); i++) { var m = ranked.get(i); out.add(new RecommendationDto(dtos.get(i), m.score(), m.reasons(), basis.get(m.listingId()))); }
        if (out.isEmpty()) {   // cold start: highest Circular Value Score, clearly labelled as such
            var top = live.stream().filter(l -> l.getCircularScore() != null).sorted(Comparator.comparing(Listing::getCircularScore).reversed()).limit(limit).toList();
            var td = mapper.toDtos(top);
            for (int i = 0; i < td.size(); i++) out.add(new RecommendationDto(td.get(i), top.get(i).getCircularScore().doubleValue(), List.of("No requirements or wishlist yet: ranked by Circular Value Score"), "Top circular value"));
        }
        return out;
    }
    private static String nz(String s) { return s == null ? "" : s; }
    private RequirementDto dto(BuyerRequirement b) {
        return new RequirementDto(b.getId(), b.getCategoryId(), b.getCategoryId() == null ? null : lookup.category(b.getCategoryId()).getName(), b.getMaxBudget(), b.getCity(),
                b.getMinCondition(), b.getIntendedUse(), b.getRequiredComponents(), b.getPreferredPathway(), b.isActive());
    }
}
