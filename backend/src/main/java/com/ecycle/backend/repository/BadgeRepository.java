package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface BadgeRepository extends JpaRepository<Badge, String> {
 List<Badge> findAllByOrderBySortOrderAsc();
}
