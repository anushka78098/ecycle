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
@Table(name = "passports")
@Getter @Setter
public class Passport {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String passportCode;
  private Long listingId;
  private Long parentId;
  private String componentType;
  private String name;
  @Enumerated(EnumType.STRING)
  private LifecycleStage stage;
  private Long currentOwnerId;
  private Instant createdAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; }
}
