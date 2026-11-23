package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
 Optional<PasswordResetToken> findByTokenHash(String h);
}
