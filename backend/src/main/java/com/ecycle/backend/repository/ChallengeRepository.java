package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {
 List<Challenge> findByActiveTrueOrderByIdAsc();
 Optional<Challenge> findByCode(String code);
}
