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
@Table(name = "dispute_messages")
@Getter @Setter
public class DisputeMessage {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long disputeId;
  private Long senderId;
  @Enumerated(EnumType.STRING)
  private MessageVisibility visibility;
  private String body;
  private Instant createdAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; }
}
