package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ProductLifecycleRepository extends JpaRepository<ProductLifecycle, Long> {
 List<ProductLifecycle> findByListingIdOrderByIdAsc(Long id);
}
