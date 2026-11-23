package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ChallengeCompletionRepository extends JpaRepository<ChallengeCompletion, Long> {
 List<ChallengeCompletion> findByUserId(Long userId);
 boolean existsByChallengeIdAndUserId(Long challengeId, Long userId);
 long countByUserId(Long userId);
}
