package com.ecycle.backend.entity;

import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "reward_redemptions")
@Getter @Setter
public class RewardRedemption {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long userId;
  private Long rewardId;
  private int pointsSpent;
  private String voucherCode;
  @Enumerated(EnumType.STRING)
  private RewardStatus status = RewardStatus.ISSUED;
  private Instant createdAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; }
}
