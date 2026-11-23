package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {
 List<Dispute> findByRaisedByOrAgainstUserOrderByCreatedAtDesc(Long a, Long b);
 List<Dispute> findTop500ByOrderByCreatedAtDesc();
 List<Dispute> findByResolverIdOrderByCreatedAtDesc(Long id);
 long countByStatus(DisputeStatus s);
 long countByStatusIn(Collection<DisputeStatus> s);
 long countByResolverIdAndStatusIn(Long id, Collection<DisputeStatus> s);
 long countByPriorityInAndStatusNotIn(Collection<DisputePriority> p, Collection<DisputeStatus> s);
 @Query(value="select cast(coalesce(avg(extract(epoch from (resolved_at - created_at)))/3600.0,0) as double precision) from disputes where status='RESOLVED' and resolved_at is not null", nativeQuery=true) Double avgResolutionHours();
}
