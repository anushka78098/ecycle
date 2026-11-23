package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface PassportEventRepository extends JpaRepository<PassportEvent, Long> {
 List<PassportEvent> findByPassportIdOrderByIdAsc(Long id);
}
