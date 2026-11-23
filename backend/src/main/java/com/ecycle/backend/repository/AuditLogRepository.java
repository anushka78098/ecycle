package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
 List<AuditLog> findTop300ByOrderByIdDesc();
 List<AuditLog> findTop300ByActionContainingIgnoreCaseOrderByIdDesc(String a);
 long countByCreatedAtAfter(Instant t);
}
