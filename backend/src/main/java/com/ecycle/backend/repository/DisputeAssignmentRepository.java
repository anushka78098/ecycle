package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DisputeAssignmentRepository extends JpaRepository<DisputeAssignment, Long> {
 List<DisputeAssignment> findByDisputeIdOrderByIdAsc(Long id);
}
