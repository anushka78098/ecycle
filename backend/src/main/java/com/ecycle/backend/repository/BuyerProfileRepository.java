package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BuyerProfileRepository extends JpaRepository<BuyerProfile, Long> {
 Optional<BuyerProfile> findByUserId(Long id);
 List<BuyerProfile> findByUserIdIn(Collection<Long> ids);
 List<BuyerProfile> findByVerifiedFalse();
}
