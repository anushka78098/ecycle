package com.ecycle.backend.service;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.engine.BidScoreEngine;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Feature B: time-limited reverse auction with Circular Bid Score. Bids are append-only (DB trigger) and scores are recomputed against the current best price. */
@Service @RequiredArgsConstructor
public class AuctionService {
    private final AuctionRepository auctions; private final BidRepository bids; private final ListingRepository listings; private final BuyerProfileRepository buyers;
    private final ScoreConfigService cfg; private final NotificationService notifications; private final AuditService audit; private final PurchaseRules rules;
    private final OrderService orderService; private final Lookup lookup;

    @Transactional
    public Auction open(Listing l) {
        int hours = l.getAuctionHours() == null ? (int) Math.round(cfg.all().getOrDefault("auction.default_hours", 72.0)) : l.getAuctionHours();
        Auction a = new Auction();
        a.setListingId(l.getId()); a.setEndsAt(Instant.now().plus(Duration.ofHours(hours)));
        a.setMinPrice(l.getAuctionMinPrice() == null ? BigDecimal.ZERO : l.getAuctionMinPrice());
        auctions.save(a);
        audit.log(l.getSellerId(), "AUCTION_OPENED", "auctions", a.getId(), "listing=" + l.getId() + " hours=" + hours);
        return a;
    }

    private BidScoreEngine engine() { return new BidScoreEngine(cfg.all()); }
    private static BigDecimal reference(Listing l, Collection<Bid> bs, BigDecimal extra) {
        BigDecimal ref = l.getExpectedPrice();
        for (Bid b : bs) if (b.getPrice().compareTo(ref) > 0) ref = b.getPrice();
        return extra != null && extra.compareTo(ref) > 0 ? extra : ref;
    }

    @Transactional
    public BidDto placeBid(AuthUser me, Long auctionId, BidRequest r) {
        lookup.requireRole(me, Role.BUYER);
        Auction a = auctions.lockById(auctionId).orElseThrow(() -> ApiException.notFound("Auction not found"));
        if (a.getStatus() != AuctionStatus.OPEN || !Instant.now().isBefore(a.getEndsAt())) throw ApiException.conflict("This auction is closed");
        Listing l = listings.findById(a.getListingId()).orElseThrow();
        BuyerProfile bp = buyers.findByUserId(me.id()).orElseThrow(() -> ApiException.forbidden("Buyer profile missing"));
        if (!bp.isVerified()) throw ApiException.forbidden("Only verified buyers can bid. Ask an admin to verify your buyer account.");
        rules.check(l, me.id(), r.pathway());
        if (r.price().compareTo(a.getMinPrice()) < 0) throw ApiException.badRequest("Bid is below the minimum price " + a.getMinPrice());
        List<Bid> existing = bids.findByAuctionIdOrderByPriceDescCreatedAtAsc(auctionId);
        Bid top = existing.isEmpty() ? null : existing.get(0);
        double score = engine().score(new BidScoreEngine.Input(r.price().doubleValue(), reference(l, existing, r.price()).doubleValue(), true, bp.getRecoveryCapability(), bp.getEnvironmentalRating()));
        Bid b = new Bid();
        b.setAuctionId(auctionId); b.setBidderId(me.id()); b.setPrice(r.price()); b.setPathway(r.pathway()); b.setRecoveryPlan(r.recoveryPlan());
        b.setRecoveryCapability(bp.getRecoveryCapability()); b.setEnvironmentalPerformance(bp.getEnvironmentalRating()); b.setCircularBidScore(BigDecimal.valueOf(score));
        bids.save(b);
        audit.log(me.id(), "BID_PLACED", "auctions", auctionId, "price=" + r.price() + " pathway=" + r.pathway() + " score=" + score);
        notifications.notify(l.getSellerId(), "New bid on " + l.getName(), "A verified buyer bid " + r.price() + " (Circular Bid Score " + score + ").", "AUCTION");
        if (top != null && !top.getBidderId().equals(me.id()) && r.price().compareTo(top.getPrice()) > 0)
            notifications.notify(top.getBidderId(), "You were outbid", "Another bidder offered more on \"" + l.getName() + "\".", "AUCTION");
        return new BidDto(b.getId(), me.id(), "You", b.getPrice(), b.getPathway(), b.getRecoveryCapability(), b.getEnvironmentalPerformance(), b.getRecoveryPlan(), score, b.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public AuctionDto view(AuthUser me, Long id) {
        Auction a = auctions.findById(id).orElseThrow(() -> ApiException.notFound("Auction not found"));
        return build(a, me, true);
    }
    @Transactional(readOnly = true)
    public AuctionDto forListing(AuthUser me, Long listingId) {
        return build(auctions.findByListingId(listingId).orElseThrow(() -> ApiException.notFound("This listing has no auction")), me, true);
    }
    @Transactional(readOnly = true)
    public List<AuctionDto> browse(AuthUser me) { return auctions.findByStatusOrderByEndsAtAsc(AuctionStatus.OPEN).stream().map(a -> build(a, me, false)).toList(); }
    @Transactional(readOnly = true)
    public List<AuctionDto> mine(AuthUser me) {
        Set<Long> ids = new LinkedHashSet<>();
        listings.findBySellerIdOrderByCreatedAtDesc(me.id()).forEach(l -> auctions.findByListingId(l.getId()).ifPresent(a -> ids.add(a.getId())));
        bids.findByBidderIdOrderByCreatedAtDesc(me.id()).forEach(b -> ids.add(b.getAuctionId()));
        return ids.stream().map(i -> auctions.findById(i).orElse(null)).filter(Objects::nonNull).map(a -> build(a, me, false)).toList();
    }

    private AuctionDto build(Auction a, AuthUser me, boolean withBids) {
        Listing l = listings.findById(a.getListingId()).orElseThrow();
        List<Bid> chrono = bids.findByAuctionIdOrderByCreatedAtAsc(a.getId());
        boolean privileged = me.has(Role.RESOLVER) || l.getSellerId().equals(me.id());
        BigDecimal ref = reference(l, chrono, null);
        BidScoreEngine eng = engine();
        Map<Long, String> anon = new LinkedHashMap<>(); for (Bid b : chrono) anon.putIfAbsent(b.getBidderId(), "Bidder " + (anon.size() + 1));
        Map<Long, String> real = privileged ? lookup.names(anon.keySet()) : Map.of();
        List<BidDto> dtos = chrono.stream().map(b -> {
            double s = eng.score(new BidScoreEngine.Input(b.getPrice().doubleValue(), ref.doubleValue(), true, b.getRecoveryCapability(), b.getEnvironmentalPerformance()));
            String label = b.getBidderId().equals(me.id()) ? "You" : privileged ? real.getOrDefault(b.getBidderId(), anon.get(b.getBidderId())) : anon.get(b.getBidderId());
            return new BidDto(b.getId(), privileged || b.getBidderId().equals(me.id()) ? b.getBidderId() : null, label, b.getPrice(), b.getPathway(), b.getRecoveryCapability(),
                    b.getEnvironmentalPerformance(), b.getRecoveryPlan(), s, b.getCreatedAt());
        }).toList();
        BidDto topPrice = dtos.stream().max(Comparator.comparing(BidDto::price).thenComparing(Comparator.comparing(BidDto::createdAt).reversed())).orElse(null);
        BidDto topScore = dtos.stream().max(Comparator.comparingDouble(BidDto::circularBidScore).thenComparing(BidDto::price)).orElse(null);
        long secs = a.getStatus() == AuctionStatus.OPEN ? Math.max(0, Duration.between(Instant.now(), a.getEndsAt()).getSeconds()) : 0;
        return new AuctionDto(a.getId(), a.getListingId(), l.getName(), a.getEndsAt(), a.getStatus(), a.getMinPrice(), chrono.size(), secs, ref, topPrice, topScore,
                withBids ? dtos : List.of(), a.getWinningBidId());
    }

    /** The seller awards the auction to one bid; an order for the bid price is created for that bidder. */
    @Transactional
    public OrderDto accept(AuthUser me, Long auctionId, Long bidId) {
        Auction a = auctions.lockById(auctionId).orElseThrow(() -> ApiException.notFound("Auction not found"));
        Listing l = listings.findById(a.getListingId()).orElseThrow();
        if (!l.getSellerId().equals(me.id())) throw ApiException.forbidden("Only the seller can accept a bid");
        if (a.getStatus() != AuctionStatus.OPEN && a.getStatus() != AuctionStatus.CLOSED_PENDING_SELLER) throw ApiException.conflict("Auction is " + a.getStatus());
        Bid b = bids.findById(bidId).filter(x -> x.getAuctionId().equals(auctionId)).orElseThrow(() -> ApiException.notFound("Bid not found in this auction"));
        OrderDto order = orderService.createFromBid(l, b);
        a.setStatus(AuctionStatus.AWARDED); a.setWinningBidId(b.getId());
        audit.log(me.id(), "BID_ACCEPTED", "auctions", auctionId, "bid=" + bidId + " price=" + b.getPrice());
        notifications.notify(b.getBidderId(), "You won the auction", "Your bid of " + b.getPrice() + " for \"" + l.getName() + "\" was accepted. Complete payment from My Orders.", "AUCTION");
        bids.findByAuctionIdOrderByCreatedAtAsc(auctionId).stream().map(Bid::getBidderId).filter(i -> !i.equals(b.getBidderId())).distinct()
                .forEach(i -> notifications.notify(i, "Auction closed", "\"" + l.getName() + "\" was awarded to another bid.", "AUCTION"));
        return order;
    }

    @Transactional
    public void cancel(AuthUser me, Long auctionId) {
        Auction a = auctions.lockById(auctionId).orElseThrow(() -> ApiException.notFound("Auction not found"));
        Listing l = listings.findById(a.getListingId()).orElseThrow();
        if (!l.getSellerId().equals(me.id())) throw ApiException.forbidden("Only the seller can cancel");
        if (a.getStatus() != AuctionStatus.OPEN) throw ApiException.conflict("Only an open auction can be cancelled");
        if (bids.countByAuctionId(auctionId) > 0) throw ApiException.conflict("An auction with bids cannot be cancelled; accept a bid or let it expire");
        a.setStatus(AuctionStatus.CANCELLED); l.setAuctionEnabled(false);
        audit.log(me.id(), "AUCTION_CANCELLED", "auctions", auctionId, null);
    }

    /** Called by the scheduler: closes every expired auction and notifies the seller and all participants. */
    @Transactional
    public int closeExpired() {
        List<Auction> due = auctions.findByStatusAndEndsAtBefore(AuctionStatus.OPEN, Instant.now());
        for (Auction a : due) {
            Listing l = listings.findById(a.getListingId()).orElseThrow();
            List<Bid> bs = bids.findByAuctionIdOrderByCreatedAtAsc(a.getId());
            if (bs.isEmpty()) {
                a.setStatus(AuctionStatus.CLOSED_NO_BIDS); l.setAuctionEnabled(false);
                notifications.notify(l.getSellerId(), "Auction ended with no bids", "\"" + l.getName() + "\" can now be sold directly.", "AUCTION");
            } else {
                a.setStatus(AuctionStatus.CLOSED_PENDING_SELLER);
                notifications.notify(l.getSellerId(), "Auction ended: choose a winner", "\"" + l.getName() + "\" received " + bs.size() + " bid(s). Review them in Auctions.", "AUCTION");
                bs.stream().map(Bid::getBidderId).distinct().forEach(i -> notifications.notify(i, "Auction ended", "Bidding on \"" + l.getName() + "\" has closed; the seller will choose a winner.", "AUCTION"));
            }
            audit.log(null, "AUCTION_CLOSED", "auctions", a.getId(), a.getStatus().name());
        }
        return due.size();
    }
}
