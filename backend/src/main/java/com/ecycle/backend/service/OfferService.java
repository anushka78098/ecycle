package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class OfferService {
    private final OfferRepository offers; private final ListingRepository listings; private final PurchaseRules rules; private final Lookup lookup;
    private final OrderService orderService; private final NotificationService notifications; private final AuditService audit;

    @Transactional
    public OfferDto make(AuthUser me, OfferRequest r) {
        lookup.requireRole(me, Role.BUYER);
        Listing l = listings.findById(r.listingId()).orElseThrow(() -> ApiException.notFound("Listing not found"));
        if (l.isAuctionEnabled()) throw ApiException.conflict("This product is sold through a reverse auction; place a bid instead");
        rules.check(l, me.id(), null);
        offers.findByListingIdAndStatus(l.getId(), OfferStatus.PENDING).stream().filter(o -> o.getBuyerId().equals(me.id())).forEach(o -> o.setStatus(OfferStatus.SUPERSEDED));
        Offer o = new Offer(); o.setListingId(l.getId()); o.setBuyerId(me.id()); o.setAmount(r.amount()); o.setMessage(r.message());
        offers.save(o);
        audit.log(me.id(), "OFFER_MADE", "offers", o.getId(), "listing=" + l.getId() + " amount=" + r.amount());
        notifications.notify(l.getSellerId(), "New offer", lookup.name(me.id()) + " offered " + r.amount() + " for \"" + l.getName() + "\".", "OFFER");
        return dto(o, l.getName(), l.getExpectedPrice(), lookup.name(me.id()));
    }

    @Transactional
    public OrderDto accept(AuthUser me, Long offerId) {
        Offer o = offers.findById(offerId).orElseThrow(() -> ApiException.notFound("Offer not found"));
        Listing l = listings.findById(o.getListingId()).orElseThrow();
        if (!l.getSellerId().equals(me.id())) throw ApiException.forbidden("Only the seller can accept offers");
        if (o.getStatus() != OfferStatus.PENDING) throw ApiException.conflict("Offer is " + o.getStatus());
        OrderDto order = orderService.createFromOffer(l, o);
        o.setStatus(OfferStatus.ACCEPTED);
        offers.findByListingIdAndStatus(l.getId(), OfferStatus.PENDING).forEach(x -> {
            x.setStatus(OfferStatus.REJECTED);
            notifications.notify(x.getBuyerId(), "Offer declined", "\"" + l.getName() + "\" was sold to another buyer.", "OFFER");
        });
        audit.log(me.id(), "OFFER_ACCEPTED", "offers", offerId, "amount=" + o.getAmount());
        notifications.notify(o.getBuyerId(), "Offer accepted", "Your offer for \"" + l.getName() + "\" was accepted. Complete payment from My Orders.", "OFFER");
        return order;
    }

    @Transactional
    public OfferDto reject(AuthUser me, Long offerId) {
        Offer o = offers.findById(offerId).orElseThrow(() -> ApiException.notFound("Offer not found"));
        Listing l = listings.findById(o.getListingId()).orElseThrow();
        if (!l.getSellerId().equals(me.id())) throw ApiException.forbidden("Only the seller can reject offers");
        if (o.getStatus() != OfferStatus.PENDING) throw ApiException.conflict("Offer is " + o.getStatus());
        o.setStatus(OfferStatus.REJECTED);
        audit.log(me.id(), "OFFER_REJECTED", "offers", offerId, null);
        notifications.notify(o.getBuyerId(), "Offer declined", "The seller declined your offer for \"" + l.getName() + "\".", "OFFER");
        return dto(o, l.getName(), l.getExpectedPrice(), lookup.name(o.getBuyerId()));
    }

    @Transactional
    public OfferDto withdraw(AuthUser me, Long offerId) {
        Offer o = offers.findById(offerId).orElseThrow(() -> ApiException.notFound("Offer not found"));
        if (!o.getBuyerId().equals(me.id())) throw ApiException.forbidden("Not your offer");
        if (o.getStatus() != OfferStatus.PENDING) throw ApiException.conflict("Offer is " + o.getStatus());
        o.setStatus(OfferStatus.WITHDRAWN);
        Listing l = listings.findById(o.getListingId()).orElseThrow();
        return dto(o, l.getName(), l.getExpectedPrice(), lookup.name(me.id()));
    }

    @Transactional(readOnly = true)
    public List<OfferDto> mine(AuthUser me) {
        return offers.findByBuyerIdOrderByCreatedAtDesc(me.id()).stream().map(o -> { Listing l = listings.findById(o.getListingId()).orElseThrow(); return dto(o, l.getName(), l.getExpectedPrice(), lookup.name(me.id())); }).toList();
    }
    @Transactional(readOnly = true)
    public List<OfferDto> forSeller(AuthUser me) {
        Map<Long, Listing> mine = listings.findBySellerIdOrderByCreatedAtDesc(me.id()).stream().collect(Collectors.toMap(Listing::getId, l -> l));
        if (mine.isEmpty()) return List.of();
        List<Offer> os = offers.findByListingIdIn(mine.keySet());
        Map<Long, String> names = lookup.names(os.stream().map(Offer::getBuyerId).collect(Collectors.toSet()));
        return os.stream().sorted(Comparator.comparing(Offer::getCreatedAt).reversed())
                .map(o -> dto(o, mine.get(o.getListingId()).getName(), mine.get(o.getListingId()).getExpectedPrice(), names.get(o.getBuyerId()))).toList();
    }
    private static OfferDto dto(Offer o, String listingName, java.math.BigDecimal asking, String buyerName) {
        return new OfferDto(o.getId(), o.getListingId(), listingName, o.getBuyerId(), buyerName, o.getAmount(), o.getMessage(), o.getStatus(), o.getCreatedAt(), asking);
    }
}
