package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface BidRepository extends JpaRepository<Bid, Long> {
 List<Bid> findByAuctionIdOrderByPriceDescCreatedAtAsc(Long id);
 List<Bid> findByAuctionIdOrderByCreatedAtAsc(Long id);
 long countByAuctionId(Long id);
 List<Bid> findByBidderIdOrderByCreatedAtDesc(Long id);
}
