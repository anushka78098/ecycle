package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;


public interface UserRepository extends JpaRepository<AppUser, Long> {
 Optional<AppUser> findByEmailIgnoreCase(String email);
 boolean existsByEmailIgnoreCase(String email);
 Optional<AppUser> findByGoogleSub(String sub);
 List<AppUser> findByRolesName(String name);
 List<AppUser> findTop300ByOrderByIdDesc();
 long countByStatus(AccountStatus s);
}
