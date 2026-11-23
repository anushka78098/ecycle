package com.ecycle.backend.service;

import com.ecycle.backend.entity.Listing;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.BuyerProfileRepository;
import com.ecycle.shared.Enums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Single place for the rules every purchase path (buy now, offer, auction bid) must satisfy, including hazard routing and data-sanitization gating. */
@Component @RequiredArgsConstructor
public class PurchaseRules {
    private final BuyerProfileRepository buyers;

    public void check(Listing l, Long buyerId, Pathway intended) {
        if (l.getSellerId().equals(buyerId)) throw ApiException.forbidden("You cannot buy your own listing");
        if (l.getStatus() != ListingStatus.APPROVED) throw ApiException.conflict("This product is not available (status " + l.getStatus() + ")");
        if (l.isShippingBlocked()) {
            boolean specialist = buyers.findByUserId(buyerId).map(b -> b.isVerified() && b.getBuyerType() == BuyerType.RECYCLER).orElse(false);
            if (!specialist) throw ApiException.conflict("Hazardous product: only verified specialist recyclers can buy it (" + l.getSpecialistRequired() + ")");
        }
        Pathway p = intended != null ? intended : l.getPathway();
        boolean resale = p == Pathway.REUSE || p == Pathway.REPAIR || p == Pathway.REFURBISH;
        if (l.isRequiresSanitization() && !l.isSanitizationVerified() && resale)
            throw ApiException.conflict("Data sanitization must be verified (certificate issued) before this product can be resold or reused");
    }
}
