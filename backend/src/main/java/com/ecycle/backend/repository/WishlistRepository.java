package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
 List<Wishlist> findByUserIdOrderByCreatedAtDesc(Long id);
 Optional<Wishlist> findByUserIdAndListingId(Long u, Long l);
 long countByUserId(Long id);
}
