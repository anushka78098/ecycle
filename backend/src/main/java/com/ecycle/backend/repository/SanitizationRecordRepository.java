package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface SanitizationRecordRepository extends JpaRepository<SanitizationRecord, Long> {
 List<SanitizationRecord> findByListingIdOrderByIdDesc(Long id);
 List<SanitizationRecord> findByStatusOrderByIdAsc(SanitizationStatus s);
 Optional<SanitizationRecord> findByCertificateNo(String certificateNo);
}
