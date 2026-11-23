package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface PickupEventRepository extends JpaRepository<PickupEvent, Long> {
 List<PickupEvent> findByPickupIdOrderByIdAsc(Long pickupId);
}
