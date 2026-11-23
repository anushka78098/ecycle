package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface HazardRuleRepository extends JpaRepository<HazardRule, Long> {
 List<HazardRule> findByEnabledTrue();
 Optional<HazardRule> findByCode(String c);
}
