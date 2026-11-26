package com.ecycle.backend.entity;

import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "eco_ledger")
@Getter @Setter
public class EcoLedger {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long userId;
  private int delta;
  private String activity;
  private String refType;
  private Long refId;
  private BigDecimal weightKg = BigDecimal.ZERO;
  private BigDecimal carbonKg = BigDecimal.ZERO;
  private String note;
  private Instant createdAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; }
}
