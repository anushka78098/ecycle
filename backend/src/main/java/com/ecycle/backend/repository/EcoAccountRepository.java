package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EcoAccountRepository extends JpaRepository<EcoAccount, Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from EcoAccount a where a.userId=:u") Optional<EcoAccount> lockByUserId(@Param("u") Long u);
 List<EcoAccount> findByShowOnLeaderboardTrueOrderByLifetimePointsDescUserIdAsc(Pageable page);
 long countByShowOnLeaderboardTrueAndLifetimePointsGreaterThan(int points);
}
