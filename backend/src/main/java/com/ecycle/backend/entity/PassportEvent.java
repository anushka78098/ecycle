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
@Table(name = "passport_events")
@Getter @Setter
public class PassportEvent {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long passportId;
  private String eventType;
  private Long actorId;
  private String detail;
  private BigDecimal valueAmount;
  private Instant createdAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; }
}
