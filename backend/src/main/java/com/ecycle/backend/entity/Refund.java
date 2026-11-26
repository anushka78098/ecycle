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
@Table(name = "refunds")
@Getter @Setter
public class Refund {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long paymentId;
  private Long orderId;
  private Long disputeId;
  private BigDecimal amount;
  private String reason;
  @Enumerated(EnumType.STRING)
  private RefundStatus status = RefundStatus.REQUESTED;
  private String providerRefundId;
  private Instant createdAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; }
}
