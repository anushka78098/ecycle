package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
 List<Review> findByRevieweeIdOrderByCreatedAtDesc(Long id);
 boolean existsByOrderIdAndReviewerId(Long o, Long r);
 @Query("select coalesce(avg(r.rating),0) from Review r where r.revieweeId=:id") Double avgRating(@Param("id") Long id);
}
