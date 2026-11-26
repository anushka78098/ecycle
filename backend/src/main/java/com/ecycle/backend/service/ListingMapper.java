package com.ecycle.backend.service;

import com.ecycle.backend.entity.*;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class ListingMapper {
    private final ProductCategoryRepository categories; private final ListingImageRepository images; private final AuctionRepository auctions;
    private final PassportRepository passports; private final Lookup lookup; private final SellerProfileRepository sellers;

    private static double d(BigDecimal b) { return b == null ? 0 : b.doubleValue(); }

    public ListingDto toDto(Listing l) { return toDtos(List.of(l)).get(0); }

    public List<ListingDto> toDtos(Collection<Listing> ls) {
        if (ls.isEmpty()) return List.of();
        Set<Long> ids = ls.stream().map(Listing::getId).collect(Collectors.toSet());
        Map<Long, String> cat = categories.findAll().stream().collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName));
        Map<Long, List<Long>> imgs = new HashMap<>();
        images.findByListingIdIn(ids).forEach(i -> imgs.computeIfAbsent(i.getListingId(), k -> new ArrayList<>()).add(i.getId()));
        Map<Long, Auction> au = auctions.findByListingIdIn(ids).stream().collect(Collectors.toMap(Auction::getListingId, a -> a));
        Map<Long, String> pass = passports.findByListingIdIn(ids).stream().collect(Collectors.toMap(Passport::getListingId, Passport::getPassportCode));
        Set<Long> sellerIds = ls.stream().map(Listing::getSellerId).collect(Collectors.toSet());
        Map<Long, String> names = lookup.names(sellerIds);
        Set<Long> verified = sellers.findByUserIdIn(sellerIds).stream().filter(s -> s.getVerificationStatus() == com.ecycle.shared.Enums.VerificationStatus.VERIFIED)
                .map(SellerProfile::getUserId).collect(Collectors.toSet());
        List<ListingDto> out = new ArrayList<>();
        for (Listing l : ls) {
            ScoreDto sc = l.getCircularScore() == null ? null : new ScoreDto(d(l.getCircularScore()), d(l.getScoreFunction()), d(l.getScoreCondition()), d(l.getScoreRepair()),
                    d(l.getScoreReuse()), d(l.getScoreComponent()), d(l.getScoreLife()), l.getRecommendedPathway(), l.getScoreExplanation());
            Auction a = au.get(l.getId());
            List<Long> im = imgs.getOrDefault(l.getId(), List.of()); im = im.stream().sorted().toList();
            out.add(new ListingDto(l.getId(), l.getSellerId(), names.get(l.getSellerId()), verified.contains(l.getSellerId()), l.getCategoryId(), cat.get(l.getCategoryId()),
                    l.getName(), l.getBrand(), l.getModel(), l.getAgeMonths(), l.getPhysicalCondition(), l.getFunctionalStatus(), l.getQuantity(), l.getExpectedPrice(),
                    l.getDescription(), l.getPickupCity(), l.getPickupAddress(), l.getWeightKg(), l.getPathway(), l.getStatus(), l.getRejectionReason(), sc,
                    l.getHazardLevel(), l.isShippingBlocked(), l.getSpecialistRequired(), l.isRequiresSanitization(), l.isSanitizationVerified(),
                    l.isAuctionEnabled(), a == null ? null : a.getId(), a == null ? null : a.getEndsAt(), im, pass.get(l.getId()), l.getCreatedAt()));
        }
        return out;
    }
}
