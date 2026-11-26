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
@Table(name = "dispute_evidence")
@Getter @Setter
public class DisputeEvidence {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long disputeId;
  private Long submittedBy;
  @Enumerated(EnumType.STRING)
  private EvidenceCategory category;
  private String description;
  private String filePath;
  private String originalName;
  private String contentType;
  private String sha256;
  private Instant createdAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; }
}
