package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
 Optional<Payment> findFirstByOrderIdOrderByIdDesc(Long orderId);
 List<Payment> findByOrderIdOrderByIdDesc(Long orderId);
}
