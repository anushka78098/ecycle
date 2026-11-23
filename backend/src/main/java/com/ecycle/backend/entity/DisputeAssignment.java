package com.ecycle.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "dispute_assignments")
@Getter @Setter
public class DisputeAssignment {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long disputeId;
  private Long resolverId;
  private Long assignedBy;
  private Instant createdAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; }
}
