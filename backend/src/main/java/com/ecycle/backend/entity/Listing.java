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
@Table(name = "listings")
@Getter @Setter
public class Listing {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long sellerId;
  private Long categoryId;
  private String name;
  private String brand;
  private String model;
  private int ageMonths = 0;
  @Enumerated(EnumType.STRING)
  private ItemCondition physicalCondition;
  @Enumerated(EnumType.STRING)
  private FunctionalStatus functionalStatus;
  private int quantity = 1;
  private BigDecimal expectedPrice;
  private String description;
  private String pickupCity;
  private String pickupAddress;
  private BigDecimal weightKg = new BigDecimal("1");
  @Enumerated(EnumType.STRING)
  private Pathway pathway;
  @Enumerated(EnumType.STRING)
  private ListingStatus status = ListingStatus.PENDING_APPROVAL;
  private String rejectionReason;
  private BigDecimal scoreFunction;
  private BigDecimal scoreCondition;
  private BigDecimal scoreRepair;
  private BigDecimal scoreReuse;
  private BigDecimal scoreComponent;
  private BigDecimal scoreLife;
  private BigDecimal circularScore;
  @Enumerated(EnumType.STRING)
  private Pathway recommendedPathway;
  private String scoreExplanation;
  @Enumerated(EnumType.STRING)
  private HazardLevel hazardLevel = HazardLevel.LOW;
  private boolean shippingBlocked = false;
  private String specialistRequired;
  private boolean requiresSanitization = false;
  private boolean sanitizationVerified = false;
  private boolean auctionEnabled = false;
  private Integer auctionHours;
  private BigDecimal auctionMinPrice;
  private Instant createdAt;
  private Instant updatedAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; updatedAt = n; }
  @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
