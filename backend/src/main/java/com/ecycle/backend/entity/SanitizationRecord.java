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
@Table(name = "sanitization_records")
@Getter @Setter
public class SanitizationRecord {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long listingId;
  private Long submittedBy;
  private String method;
  private String toolName;
  private String evidenceRef;
  @Enumerated(EnumType.STRING)
  private SanitizationStatus status = SanitizationStatus.SUBMITTED;
  private Long verifiedBy;
  private Instant verifiedAt;
  private String certificateNo;
  private String note;
  private Instant createdAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; }
}
