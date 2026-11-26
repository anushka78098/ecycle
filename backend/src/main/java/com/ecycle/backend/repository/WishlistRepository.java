package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
 List<Wishlist> findByUserIdOrderByCreatedAtDesc(Long id);
 Optional<Wishlist> findByUserIdAndListingId(Long u, Long l);
 long countByUserId(Long id);
}
