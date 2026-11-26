package com.ecycle.backend.scheduler;

import com.ecycle.backend.service.AuctionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Closes expired reverse auctions every minute (bids are also rejected at the deadline even if this job is late). */
@Component @RequiredArgsConstructor
public class AuctionScheduler {
    private static final Logger log = LoggerFactory.getLogger(AuctionScheduler.class);
    private final AuctionService auctions;

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    public void closeExpired() {
        try { int n = auctions.closeExpired(); if (n > 0) log.info("Closed {} expired auction(s)", n); }
        catch (RuntimeException e) { log.error("Auction closing job failed", e); }
    }
}
