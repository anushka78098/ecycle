package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ScoreConfigRepository extends JpaRepository<ScoreConfig, Long> {
 Optional<ScoreConfig> findByConfigKey(String k);
}
