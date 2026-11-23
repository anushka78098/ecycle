package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DisputeMessageRepository extends JpaRepository<DisputeMessage, Long> {
 List<DisputeMessage> findByDisputeIdOrderByCreatedAtAsc(Long id);
}
