package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface HazardInspectionRepository extends JpaRepository<HazardInspection, Long> {
 List<HazardInspection> findByListingIdOrderByIdDesc(Long id);
}
