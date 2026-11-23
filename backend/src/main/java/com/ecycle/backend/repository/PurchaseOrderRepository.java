package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
 List<PurchaseOrder> findByBuyerIdOrderByCreatedAtDesc(Long id);
 List<PurchaseOrder> findBySellerIdOrderByCreatedAtDesc(Long id);
 List<PurchaseOrder> findTop500ByOrderByCreatedAtDesc();
 Optional<PurchaseOrder> findFirstByListingIdAndStatusInOrderByIdDesc(Long l, Collection<OrderStatus> s);
 boolean existsByListingId(Long id);
 Optional<PurchaseOrder> findFirstByListingIdOrderByIdDesc(Long id);
 long countByBuyerId(Long id);
 long countByBuyerIdAndStatusIn(Long id, Collection<OrderStatus> s);
 long countBySellerIdAndStatusIn(Long id, Collection<OrderStatus> s);
 long countByStatus(OrderStatus s);
 @Query("select coalesce(sum(o.amount),0) from PurchaseOrder o where o.sellerId=:uid and o.status in :st") BigDecimal sumSellerAmount(@Param("uid") Long uid, @Param("st") Collection<OrderStatus> st);
 @Query("select coalesce(sum(o.amount),0) from PurchaseOrder o where o.buyerId=:uid and o.status in :st") BigDecimal sumBuyerAmount(@Param("uid") Long uid, @Param("st") Collection<OrderStatus> st);
 @Query("select coalesce(sum(o.amount),0) from PurchaseOrder o where o.status in :st") BigDecimal sumAll(@Param("st") Collection<OrderStatus> st);
}
