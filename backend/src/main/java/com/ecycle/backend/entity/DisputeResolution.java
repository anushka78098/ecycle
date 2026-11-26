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
@Table(name = "dispute_resolutions")
@Getter @Setter
public class DisputeResolution {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long disputeId;
  private Long resolverId;
  @Enumerated(EnumType.STRING)
  private ResolutionType resolutionType;
  private BigDecimal refundAmount;
  private String decisionText;
  private Instant createdAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; }
}
