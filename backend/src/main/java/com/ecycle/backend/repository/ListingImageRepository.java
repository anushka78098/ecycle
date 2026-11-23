package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ListingImageRepository extends JpaRepository<ListingImage, Long> {
 List<ListingImage> findByListingIdOrderByIdAsc(Long id);
 List<ListingImage> findByListingIdIn(Collection<Long> ids);
 long countByListingId(Long id);
}
