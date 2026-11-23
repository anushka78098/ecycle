package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DisputeResolutionRepository extends JpaRepository<DisputeResolution, Long> {
 Optional<DisputeResolution> findByDisputeId(Long id);
 List<DisputeResolution> findByResolverIdOrderByCreatedAtDesc(Long id);
}
