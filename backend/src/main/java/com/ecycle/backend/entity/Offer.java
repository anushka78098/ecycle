package com.ecycle.backend.entity;

import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "offers")
@Getter @Setter
public class Offer {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long listingId;
  private Long buyerId;
  private BigDecimal amount;
  private String message;
  @Enumerated(EnumType.STRING)
  private OfferStatus status = OfferStatus.PENDING;
  private Instant createdAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; }
}
