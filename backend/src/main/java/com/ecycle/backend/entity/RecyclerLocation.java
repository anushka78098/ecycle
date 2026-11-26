package com.ecycle.backend.entity;

import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "recycler_locations")
@Getter @Setter
public class RecyclerLocation {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long recyclerId;
  private String facilityName;
  private String address;
  private String city;
  private Double latitude;
  private Double longitude;
  private String phone;
  private String openingHours;
  private String acceptedCategories;
  private String certifications;
  private int serviceRadiusKm = 25;
  private boolean active = true;
  private Instant createdAt;
  private Instant updatedAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; updatedAt = n; }
  @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
