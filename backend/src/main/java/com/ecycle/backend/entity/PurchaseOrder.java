package com.ecycle.backend.entity;

import com.ecycle.shared.Enums;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "orders")
@Getter @Setter
public class PurchaseOrder {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long listingId;
  private Long buyerId;
  private Long sellerId;
  private Long offerId;
  private Long bidId;
  private int quantity = 1;
  private BigDecimal amount;
  @Enumerated(EnumType.STRING)
  private OrderStatus status = OrderStatus.PENDING_PAYMENT;
  private String shippingAddress;
  private String cancelReason;
  private Instant createdAt;
  private Instant updatedAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; updatedAt = n; }
  @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
