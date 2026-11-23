package com.ecycle.backend.entity;

import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "hazard_inspections")
@Getter @Setter
public class HazardInspection {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long listingId;
  private Long inspectorId;
  private String answers;
  private int totalPoints;
  @Enumerated(EnumType.STRING)
  private HazardLevel level;
  private String explanation;
  private String handling;
  private boolean blocked = false;
  private String specialist;
  private Instant createdAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; }
}
