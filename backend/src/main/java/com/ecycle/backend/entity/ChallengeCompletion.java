package com.ecycle.backend.entity;

import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "challenge_completions")
@Getter @Setter
public class ChallengeCompletion {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long challengeId;
  private Long userId;
  private int bonusPoints;
  private Instant completedAt;
  @PrePersist void prePersist() { if (completedAt == null) completedAt = Instant.now(); }
}
