package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface RecyclerLocationRepository extends JpaRepository<RecyclerLocation, Long> {
 List<RecyclerLocation> findByActiveTrue();
 List<RecyclerLocation> findByActiveTrueAndLatitudeBetweenAndLongitudeBetween(Double minLat, Double maxLat, Double minLon, Double maxLon);
 List<RecyclerLocation> findByRecyclerIdOrderByIdAsc(Long recyclerId);
}
