package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AppRoleRepository extends JpaRepository<AppRole, Long> {
 Optional<AppRole> findByName(String name);
}
