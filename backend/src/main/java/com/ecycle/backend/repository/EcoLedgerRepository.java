package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EcoLedgerRepository extends JpaRepository<EcoLedger, Long> {
 List<EcoLedger> findTop50ByUserIdOrderByIdDesc(Long userId);
 boolean existsByUserIdAndActivityAndRefTypeAndRefId(Long userId, String activity, String refType, Long refId);
 List<EcoLedger> findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(Long userId, Instant after);
 /** activity, row count, sum weight, sum carbon, sum of positive points within [from, to). */
 @Query("select l.activity, count(l), coalesce(sum(l.weightKg),0), coalesce(sum(l.carbonKg),0), coalesce(sum(case when l.delta>0 then l.delta else 0 end),0) from EcoLedger l where l.userId=:u and l.createdAt>=:from and l.createdAt<:to group by l.activity")
 List<Object[]> stats(@Param("u") Long userId, @Param("from") Instant from, @Param("to") Instant to);
}
