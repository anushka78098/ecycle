package com.ecycle.backend.entity;

import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "product_lifecycle")
@Getter @Setter
public class ProductLifecycle {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long listingId;
  @Enumerated(EnumType.STRING)
  private LifecycleStage stage;
  @Enumerated(EnumType.STRING)
  private Pathway pathway;
  private Long actorId;
  private String note;
  private Instant createdAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; }
}
