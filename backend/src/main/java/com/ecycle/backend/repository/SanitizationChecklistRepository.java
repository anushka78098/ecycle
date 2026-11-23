package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface SanitizationChecklistRepository extends JpaRepository<SanitizationChecklist, Long> {
 List<SanitizationChecklist> findByListingIdOrderByIdDesc(Long listingId);
 List<SanitizationChecklist> findByPickupIdOrderByIdDesc(Long pickupId);
}
