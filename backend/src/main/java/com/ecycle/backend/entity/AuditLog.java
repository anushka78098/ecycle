package com.ecycle.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "audit_logs")
@Getter @Setter
public class AuditLog {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long actorId;
  private String action;
  private String entityType;
  private Long entityId;
  private String detail;
  private Instant createdAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; }
}
