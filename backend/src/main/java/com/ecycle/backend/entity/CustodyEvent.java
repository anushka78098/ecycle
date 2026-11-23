package com.ecycle.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "custody_events")
@Getter @Setter
public class CustodyEvent {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String assetCode;
  private int seq;
  private String eventType;
  private Long actorId;
  private String actorRole;
  private Long custodianId;
  private String location;
  private String detail;
  private Instant occurredAt;
  private String prevHash;
  private String hash;
}
