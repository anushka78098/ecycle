package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AuctionRepository extends JpaRepository<Auction, Long> {
 Optional<Auction> findByListingId(Long id);
 List<Auction> findByListingIdIn(Collection<Long> ids);
 List<Auction> findByStatusAndEndsAtBefore(AuctionStatus s, Instant t);
 List<Auction> findByStatusOrderByEndsAtAsc(AuctionStatus s);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from Auction a where a.id=:id") Optional<Auction> lockById(@Param("id") Long id);
}
