package com.ecycle.backend.entity;

import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "challenges")
@Getter @Setter
public class Challenge {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String code;
  private String title;
  private String description;
  private String metric;
  private BigDecimal target;
  private int rewardPoints;
  private Instant startsAt;
  private Instant endsAt;
  private boolean active = true;
  @PrePersist void prePersist() { if (startsAt == null) startsAt = Instant.now(); }
}
