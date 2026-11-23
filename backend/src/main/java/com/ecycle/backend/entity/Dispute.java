package com.ecycle.backend.entity;

import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "disputes")
@Getter @Setter
public class Dispute {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long orderId;
  private Long raisedBy;
  private Long againstUser;
  private Long resolverId;
  @Enumerated(EnumType.STRING)
  private DisputeCategory category;
  private String description;
  @Enumerated(EnumType.STRING)
  private DisputeStatus status = DisputeStatus.OPEN;
  @Enumerated(EnumType.STRING)
  private DisputePriority priority = DisputePriority.MEDIUM;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant resolvedAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; updatedAt = n; }
  @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
