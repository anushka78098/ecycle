package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ChallengeCompletionRepository extends JpaRepository<ChallengeCompletion, Long> {
 List<ChallengeCompletion> findByUserId(Long userId);
 boolean existsByChallengeIdAndUserId(Long challengeId, Long userId);
 long countByUserId(Long userId);
}
