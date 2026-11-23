package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface CustodyEventRepository extends JpaRepository<CustodyEvent, Long> {
 List<CustodyEvent> findByAssetCodeOrderBySeqAsc(String assetCode);
 Optional<CustodyEvent> findFirstByAssetCodeOrderBySeqDesc(String assetCode);
 long countByAssetCode(String assetCode);
}
