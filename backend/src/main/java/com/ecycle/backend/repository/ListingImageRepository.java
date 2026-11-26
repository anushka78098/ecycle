package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ListingImageRepository extends JpaRepository<ListingImage, Long> {
 List<ListingImage> findByListingIdOrderByIdAsc(Long id);
 List<ListingImage> findByListingIdIn(Collection<Long> ids);
 long countByListingId(Long id);
}
