package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PickupRepository extends JpaRepository<Pickup, Long> {
 Optional<Pickup> findByTrackingCode(String code);
 List<Pickup> findByRequesterIdOrderByCreatedAtDesc(Long id);
 List<Pickup> findByRecyclerIdOrderByCreatedAtDesc(Long id);
 List<Pickup> findTop300ByOrderByCreatedAtDesc();
 Optional<Pickup> findFirstByListingIdAndStatusIn(Long listingId, Collection<PickupStatus> statuses);
 long countByRequesterIdAndStatus(Long id, PickupStatus s);
 long countByRecyclerIdAndStatus(Long id, PickupStatus s);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from Pickup p where p.id=:id") Optional<Pickup> lockById(@Param("id") Long id);
}
