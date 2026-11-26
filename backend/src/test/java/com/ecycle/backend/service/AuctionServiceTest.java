package com.ecycle.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ecycle.backend.config.AuthUser;
import com.ecycle.backend.entity.*;
import com.ecycle.backend.exception.ApiException;
import com.ecycle.backend.repository.*;
import com.ecycle.shared.Dtos.*;
import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class) @MockitoSettings(strictness = Strictness.LENIENT)
class AuctionServiceTest {
    @Mock AuctionRepository auctions; @Mock BidRepository bids; @Mock ListingRepository listings; @Mock BuyerProfileRepository buyers; @Mock ScoreConfigService cfg;
    @Mock NotificationService notifications; @Mock AuditService audit; @Mock PurchaseRules rules; @Mock OrderService orderService; @Mock Lookup lookup;
    @InjectMocks AuctionService svc;
    static final AuthUser SELLER = new AuthUser(1L, "s", Set.of(Role.SELLER)), BUYER = new AuthUser(2L, "b", Set.of(Role.BUYER));
    Auction auction; Listing listing; BuyerProfile profile;

    @BeforeEach void setUp() {
        listing = new Listing(); listing.setId(10L); listing.setSellerId(1L); listing.setName("PCB lot"); listing.setExpectedPrice(new BigDecimal("2000")); listing.setAuctionEnabled(true);
        auction = new Auction(); auction.setId(5L); auction.setListingId(10L); auction.setStatus(AuctionStatus.OPEN); auction.setEndsAt(Instant.now().plusSeconds(3600)); auction.setMinPrice(new BigDecimal("1000"));
        profile = new BuyerProfile(); profile.setVerified(true); profile.setRecoveryCapability(80); profile.setEnvironmentalRating(70);
        when(auctions.lockById(5L)).thenReturn(Optional.of(auction)); when(auctions.findById(5L)).thenReturn(Optional.of(auction)); when(listings.findById(10L)).thenReturn(Optional.of(listing));
        when(buyers.findByUserId(2L)).thenReturn(Optional.of(profile)); when(cfg.all()).thenReturn(Map.of());
        when(bids.findByAuctionIdOrderByPriceDescCreatedAtAsc(5L)).thenReturn(List.of()); when(bids.save(any())).thenAnswer(i -> { Bid b = i.getArgument(0); b.setId(77L); b.setCreatedAt(Instant.now()); return b; });
    }
    private static BidRequest bid(String price) { return new BidRequest(new BigDecimal(price), Pathway.RECYCLE, "plan"); }

    @Test void verifiedBuyerBidIsScoredStoredAndSellerNotified() {
        BidDto d = svc.placeBid(BUYER, 5L, bid("1500"));
        assertTrue(d.circularBidScore() > 0 && d.circularBidScore() <= 100);
        ArgumentCaptor<Bid> c = ArgumentCaptor.forClass(Bid.class); verify(bids).save(c.capture());
        assertEquals(80, c.getValue().getRecoveryCapability()); assertEquals(70, c.getValue().getEnvironmentalPerformance());   // from the verified profile, not self-declared
        verify(notifications).notify(eq(1L), anyString(), anyString(), eq("AUCTION"));
    }
    @Test void unverifiedBuyerCannotBid() { profile.setVerified(false); assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> svc.placeBid(BUYER, 5L, bid("1500"))).status()); verify(bids, never()).save(any()); }
    @Test void bidAfterDeadlineIsRejectedEvenIfSchedulerIsLate() { auction.setEndsAt(Instant.now().minusSeconds(1)); assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> svc.placeBid(BUYER, 5L, bid("1500"))).status()); }
    @Test void closedAuctionRejectsBids() { auction.setStatus(AuctionStatus.CLOSED_PENDING_SELLER); assertThrows(ApiException.class, () -> svc.placeBid(BUYER, 5L, bid("1500"))); }
    @Test void bidBelowMinimumIsRejected() { assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> svc.placeBid(BUYER, 5L, bid("999"))).status()); }
    @Test void purchaseRuleViolationsPropagate() { doThrow(ApiException.conflict("hazard")).when(rules).check(any(), any(), any()); assertThrows(ApiException.class, () -> svc.placeBid(BUYER, 5L, bid("1500"))); verify(bids, never()).save(any()); }
    @Test void previousTopBidderIsToldTheyWereOutbid() {
        Bid top = new Bid(); top.setBidderId(3L); top.setPrice(new BigDecimal("1500")); when(bids.findByAuctionIdOrderByPriceDescCreatedAtAsc(5L)).thenReturn(List.of(top));
        svc.placeBid(BUYER, 5L, bid("1800")); verify(notifications).notify(eq(3L), contains("outbid"), anyString(), eq("AUCTION"));
    }
    @Test void onlyTheSellerCanAcceptAndAcceptanceAwardsTheAuction() {
        Bid b = new Bid(); b.setId(77L); b.setAuctionId(5L); b.setBidderId(2L); b.setPrice(new BigDecimal("1800")); when(bids.findById(77L)).thenReturn(Optional.of(b));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> svc.accept(BUYER, 5L, 77L)).status());
        svc.accept(SELLER, 5L, 77L); assertEquals(AuctionStatus.AWARDED, auction.getStatus()); assertEquals(77L, auction.getWinningBidId()); verify(orderService).createFromBid(listing, b);
        assertThrows(ApiException.class, () -> svc.accept(SELLER, 5L, 77L));   // cannot award twice
    }
    @Test void bidFromAnotherAuctionCannotBeAccepted() {
        Bid b = new Bid(); b.setId(78L); b.setAuctionId(99L); when(bids.findById(78L)).thenReturn(Optional.of(b)); assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> svc.accept(SELLER, 5L, 78L)).status());
    }
    @Test void expiredAuctionWithoutBidsClosesAndReleasesDirectSale() {
        auction.setEndsAt(Instant.now().minusSeconds(5)); when(auctions.findByStatusAndEndsAtBefore(eq(AuctionStatus.OPEN), any())).thenReturn(List.of(auction)); when(bids.findByAuctionIdOrderByCreatedAtAsc(5L)).thenReturn(List.of());
        assertEquals(1, svc.closeExpired()); assertEquals(AuctionStatus.CLOSED_NO_BIDS, auction.getStatus()); assertFalse(listing.isAuctionEnabled());
    }
    @Test void expiredAuctionWithBidsWaitsForSellerAndNotifiesEveryone() {
        Bid b1 = new Bid(); b1.setBidderId(2L); Bid b2 = new Bid(); b2.setBidderId(3L); when(bids.findByAuctionIdOrderByCreatedAtAsc(5L)).thenReturn(List.of(b1, b2));
        when(auctions.findByStatusAndEndsAtBefore(eq(AuctionStatus.OPEN), any())).thenReturn(List.of(auction)); svc.closeExpired();
        assertEquals(AuctionStatus.CLOSED_PENDING_SELLER, auction.getStatus()); verify(notifications, times(3)).notify(anyLong(), anyString(), anyString(), eq("AUCTION"));
    }
    @Test void cancelRulesProtectBidders() {
        when(bids.countByAuctionId(5L)).thenReturn(2L); assertThrows(ApiException.class, () -> svc.cancel(SELLER, 5L));
        when(bids.countByAuctionId(5L)).thenReturn(0L); svc.cancel(SELLER, 5L); assertEquals(AuctionStatus.CANCELLED, auction.getStatus());
    }
}
