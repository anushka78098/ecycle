package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {
 Optional<Certificate> findByCertificateNo(String no);
 List<Certificate> findByHolderIdOrderByIdDesc(Long holderId);
 List<Certificate> findByListingIdOrderByIdDesc(Long listingId);
 List<Certificate> findByPickupIdOrderByIdAsc(Long pickupId);
 Optional<Certificate> findBySanitizationRecordId(Long recordId);
 List<Certificate> findTop300ByOrderByIdDesc();
}
