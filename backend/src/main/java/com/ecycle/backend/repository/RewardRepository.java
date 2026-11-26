package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RewardRepository extends JpaRepository<Reward, Long> {
 List<Reward> findByActiveTrueOrderByCostPointsAsc();
 Optional<Reward> findByCode(String code);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from Reward r where r.id=:id") Optional<Reward> lockById(@Param("id") Long id);
}
