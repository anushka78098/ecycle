package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DisputeEvidenceRepository extends JpaRepository<DisputeEvidence, Long> {
 List<DisputeEvidence> findByDisputeIdOrderByCreatedAtAsc(Long id);
}
