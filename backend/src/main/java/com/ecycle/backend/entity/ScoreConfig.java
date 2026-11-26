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
@Table(name = "score_config")
@Getter @Setter
public class ScoreConfig {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String configKey;
  private BigDecimal configValue;
  private String description;
}
