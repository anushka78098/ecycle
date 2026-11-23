package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface RewardRedemptionRepository extends JpaRepository<RewardRedemption, Long> {
 List<RewardRedemption> findByUserIdOrderByIdDesc(Long userId);
}
