package com.ecycle.backend.entity;

import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sanitization_checklists")
@Getter @Setter
public class SanitizationChecklist {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long listingId;
  private Long recordId;
  private Long pickupId;
  private Long completedBy;
  private String completedRole;
  private String method;
  private String deviceSerial;
  private String items;
  private boolean allMandatoryDone;
  private Instant createdAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; }
}
