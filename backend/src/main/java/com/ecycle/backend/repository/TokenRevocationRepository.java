package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.time.Instant;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TokenRevocationRepository extends JpaRepository<TokenRevocation, Long> {
 boolean existsByJti(String jti);
 @Modifying @Query("delete from TokenRevocation t where t.expiresAt < :t") int purge(@Param("t") Instant t);
}
