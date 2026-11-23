package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PassportRepository extends JpaRepository<Passport, Long> {
 Optional<Passport> findByPassportCode(String code);
 Optional<Passport> findByListingId(Long id);
 List<Passport> findByParentIdOrderByIdAsc(Long id);
 List<Passport> findByListingIdIn(Collection<Long> ids);
 List<Passport> findByCurrentOwnerIdOrderByCreatedAtDesc(Long id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from Passport p where p.passportCode=:c") Optional<Passport> lockByCode(@Param("c") String c);
}
