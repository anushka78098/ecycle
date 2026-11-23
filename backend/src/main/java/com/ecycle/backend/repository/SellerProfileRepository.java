package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, Long> {
 Optional<SellerProfile> findByUserId(Long id);
 List<SellerProfile> findByUserIdIn(Collection<Long> ids);
 List<SellerProfile> findByVerificationStatus(VerificationStatus s);
}
