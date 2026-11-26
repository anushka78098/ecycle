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
@Table(name = "buyer_requirements")
@Getter @Setter
public class BuyerRequirement {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long buyerId;
  private Long categoryId;
  private BigDecimal maxBudget;
  private String city;
  @Enumerated(EnumType.STRING)
  private ItemCondition minCondition;
  private String intendedUse;
  private String requiredComponents;
  @Enumerated(EnumType.STRING)
  private Pathway preferredPathway;
  private boolean active = true;
  private Instant createdAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; }
}
