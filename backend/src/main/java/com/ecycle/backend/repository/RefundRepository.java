package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, Long> {
 List<Refund> findByPaymentId(Long id);
 @Query("select coalesce(sum(r.amount),0) from Refund r where r.paymentId=:pid and r.status=:st") BigDecimal sumByStatus(@Param("pid") Long pid, @Param("st") RefundStatus st);
}
