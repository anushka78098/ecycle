package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BidRepository extends JpaRepository<Bid, Long> {
 List<Bid> findByAuctionIdOrderByPriceDescCreatedAtAsc(Long id);
 List<Bid> findByAuctionIdOrderByCreatedAtAsc(Long id);
 long countByAuctionId(Long id);
 List<Bid> findByBidderIdOrderByCreatedAtDesc(Long id);
}
