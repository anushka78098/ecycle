package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface HazardInspectionRepository extends JpaRepository<HazardInspection, Long> {
 List<HazardInspection> findByListingIdOrderByIdDesc(Long id);
}
