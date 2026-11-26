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
@Table(name = "environmental_factors")
@Getter @Setter
public class EnvironmentalFactor {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long categoryId;
  private BigDecimal carbonReusePerKg = new BigDecimal("0");
  private BigDecimal carbonRepairPerKg = new BigDecimal("0");
  private BigDecimal carbonRefurbishPerKg = new BigDecimal("0");
  private BigDecimal carbonRecyclePerKg = new BigDecimal("0");
  private BigDecimal metalsRecoveryPct = new BigDecimal("0");
  private String sourceNote;
  private Long updatedBy;
  private Instant updatedAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); updatedAt = n; }
  @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
