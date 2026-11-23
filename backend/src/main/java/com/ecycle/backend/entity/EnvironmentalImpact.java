package com.ecycle.backend.entity;

import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "environmental_impact")
@Getter @Setter
public class EnvironmentalImpact {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long listingId;
  private Long orderId;
  private Long sellerId;
  private Long buyerId;
  @Enumerated(EnumType.STRING)
  private Pathway pathway;
  private int quantity = 1;
  private BigDecimal weightKg;
  private BigDecimal ewasteDivertedKg;
  private BigDecimal carbonAvoidedKg;
  private BigDecimal materialsRecoveredKg;
  private boolean measured = false;
  private String methodology;
  private String inputs;
  private Long recordedBy;
  private Instant createdAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; }
}
