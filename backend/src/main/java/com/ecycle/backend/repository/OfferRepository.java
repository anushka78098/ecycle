package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OfferRepository extends JpaRepository<Offer, Long> {
 List<Offer> findByListingIdOrderByAmountDesc(Long id);
 List<Offer> findByBuyerIdOrderByCreatedAtDesc(Long id);
 List<Offer> findByListingIdIn(Collection<Long> ids);
 List<Offer> findByListingIdAndStatus(Long id, OfferStatus s);
 boolean existsByListingIdAndBuyerIdAndStatus(Long l, Long b, OfferStatus s);
 long countByBuyerIdAndStatus(Long id, OfferStatus s);
 long countByListingIdInAndStatus(Collection<Long> ids, OfferStatus s);
}
