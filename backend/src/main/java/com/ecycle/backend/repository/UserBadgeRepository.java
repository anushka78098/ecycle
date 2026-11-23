package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;


public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {
 List<UserBadge> findByUserId(Long userId);
 boolean existsByUserIdAndBadgeCode(Long userId, String code);
}
