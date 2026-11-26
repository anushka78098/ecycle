package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CustodyEventRepository extends JpaRepository<CustodyEvent, Long> {
 List<CustodyEvent> findByAssetCodeOrderBySeqAsc(String assetCode);
 Optional<CustodyEvent> findFirstByAssetCodeOrderBySeqDesc(String assetCode);
 long countByAssetCode(String assetCode);
}
