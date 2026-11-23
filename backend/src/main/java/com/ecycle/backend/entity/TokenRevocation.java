package com.ecycle.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "token_revocations")
@Getter @Setter
public class TokenRevocation {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String jti;
  private Instant expiresAt;
}
