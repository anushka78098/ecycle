package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RecyclerLocationRepository extends JpaRepository<RecyclerLocation, Long> {
 List<RecyclerLocation> findByActiveTrue();
 List<RecyclerLocation> findByActiveTrueAndLatitudeBetweenAndLongitudeBetween(Double minLat, Double maxLat, Double minLon, Double maxLon);
 List<RecyclerLocation> findByRecyclerIdOrderByIdAsc(Long recyclerId);
}
