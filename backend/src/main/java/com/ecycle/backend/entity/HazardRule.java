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
@Table(name = "hazard_rules")
@Getter @Setter
public class HazardRule {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String code;
  private String description;
  private int points;
  private boolean blocksShipping = false;
  private boolean requiresSanitization = false;
  private String handling;
  private String specialistType;
  private boolean enabled = true;
}
