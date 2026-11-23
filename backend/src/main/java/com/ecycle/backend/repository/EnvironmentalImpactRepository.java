package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EnvironmentalImpactRepository extends JpaRepository<EnvironmentalImpact, Long> {
 @Query("select i.pathway, count(i), coalesce(sum(i.ewasteDivertedKg),0), coalesce(sum(i.carbonAvoidedKg),0), coalesce(sum(i.materialsRecoveredKg),0), coalesce(sum(case when i.measured=true then 1 else 0 end),0) from EnvironmentalImpact i group by i.pathway") List<Object[]> summaryAll();
 @Query("select i.pathway, count(i), coalesce(sum(i.ewasteDivertedKg),0), coalesce(sum(i.carbonAvoidedKg),0), coalesce(sum(i.materialsRecoveredKg),0), coalesce(sum(case when i.measured=true then 1 else 0 end),0) from EnvironmentalImpact i where i.sellerId=:uid group by i.pathway") List<Object[]> summaryBySeller(@Param("uid") Long uid);
 @Query("select i.pathway, count(i), coalesce(sum(i.ewasteDivertedKg),0), coalesce(sum(i.carbonAvoidedKg),0), coalesce(sum(i.materialsRecoveredKg),0), coalesce(sum(case when i.measured=true then 1 else 0 end),0) from EnvironmentalImpact i where i.buyerId=:uid group by i.pathway") List<Object[]> summaryByBuyer(@Param("uid") Long uid);
 List<EnvironmentalImpact> findByListingIdOrderByCreatedAtDesc(Long id);
}
