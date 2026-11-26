package com.ecycle.backend.entity;

import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "pickups")
@Getter @Setter
public class Pickup {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String trackingCode;
  private Long requesterId;
  private Long recyclerId;
  private Long locationId;
  private Long listingId;
  private String passportCode;
  private Long categoryId;
  private String itemsDescription;
  private int quantity = 1;
  private BigDecimal estWeightKg;
  private BigDecimal actualWeightKg;
  private boolean dataBearing = false;
  private boolean hazardous = false;
  private String pickupAddress;
  private Double pickupLat;
  private Double pickupLng;
  private String contactPhone;
  private Instant preferredAt;
  private Instant scheduledAt;
  @Enumerated(EnumType.STRING)
  private PickupStatus status = PickupStatus.REQUESTED;
  private String statusNote;
  private Double lastLat;
  private Double lastLng;
  private Instant lastPingAt;
  private BigDecimal estCarbonKg = BigDecimal.ZERO;
  private String treatment;
  private String verificationNote;
  private Instant completedAt;
  private Instant createdAt;
  private Instant updatedAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; updatedAt = n; }
  @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
