package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {
 Optional<Certificate> findByCertificateNo(String no);
 List<Certificate> findByHolderIdOrderByIdDesc(Long holderId);
 List<Certificate> findByListingIdOrderByIdDesc(Long listingId);
 List<Certificate> findByPickupIdOrderByIdAsc(Long pickupId);
 Optional<Certificate> findBySanitizationRecordId(Long recordId);
 List<Certificate> findTop300ByOrderByIdDesc();
}
