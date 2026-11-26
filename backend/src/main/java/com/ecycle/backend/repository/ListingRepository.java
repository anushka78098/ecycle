package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ListingRepository extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {
 List<Listing> findBySellerIdOrderByCreatedAtDesc(Long sellerId);
 List<Listing> findByStatusOrderByCreatedAtAsc(ListingStatus s);
 List<Listing> findByStatus(ListingStatus s);
 List<Listing> findByIdIn(Collection<Long> ids);
 long countBySellerId(Long id);
 long countBySellerIdAndStatus(Long id, ListingStatus s);
 long countByStatus(ListingStatus s);
 boolean existsByCategoryId(Long id);
}
