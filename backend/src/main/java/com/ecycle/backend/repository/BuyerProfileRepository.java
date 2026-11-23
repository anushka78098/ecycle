package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface BuyerProfileRepository extends JpaRepository<BuyerProfile, Long> {
 Optional<BuyerProfile> findByUserId(Long id);
 List<BuyerProfile> findByUserIdIn(Collection<Long> ids);
 List<BuyerProfile> findByVerifiedFalse();
}
