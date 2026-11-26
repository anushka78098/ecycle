package com.ecycle.backend.entity;

import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "eco_accounts")
@Getter @Setter
public class EcoAccount {
  @Id
  private Long userId;
  private int balance = 0;
  private int lifetimePoints = 0;
  private boolean showOnLeaderboard = true;
  private Instant updatedAt;
  @PrePersist @PreUpdate void touch() { updatedAt = Instant.now(); }
}
