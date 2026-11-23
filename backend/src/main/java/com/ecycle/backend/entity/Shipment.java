package com.ecycle.backend.entity;

import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "shipments")
@Getter @Setter
public class Shipment {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long orderId;
  private String carrier;
  private String trackingNo;
  @Enumerated(EnumType.STRING)
  private ShipmentStatus status = ShipmentStatus.PENDING;
  private boolean specialistHandling = false;
  private Instant updatedAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); updatedAt = n; }
  @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
