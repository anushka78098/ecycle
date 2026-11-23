package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
 List<ProductCategory> findByActiveTrueOrderByName();
}
