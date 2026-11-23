package com.ecycle.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_badges")
@Getter @Setter
public class UserBadge {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long userId;
  private String badgeCode;
  private Instant awardedAt;
  @PrePersist void prePersist() { if (awardedAt == null) awardedAt = Instant.now(); }
}
