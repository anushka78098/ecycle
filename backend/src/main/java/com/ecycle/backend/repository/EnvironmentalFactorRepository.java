package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface EnvironmentalFactorRepository extends JpaRepository<EnvironmentalFactor, Long> {
 Optional<EnvironmentalFactor> findByCategoryId(Long id);
}
