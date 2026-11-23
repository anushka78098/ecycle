package com.ecycle.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
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
