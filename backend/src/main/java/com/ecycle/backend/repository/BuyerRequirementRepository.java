package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface BuyerRequirementRepository extends JpaRepository<BuyerRequirement, Long> {
 List<BuyerRequirement> findByBuyerIdOrderByCreatedAtDesc(Long id);
 List<BuyerRequirement> findByBuyerIdAndActiveTrue(Long id);
}
