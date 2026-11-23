package com.ecycle.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "product_categories")
@Getter @Setter
public class ProductCategory {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String name;
  private String description;
  private int repairabilityBase = 50;
  private int componentRecoveryBase = 40;
  private int lifeMonths = 60;
  private BigDecimal defaultWeightKg = new BigDecimal("1");
  private boolean dismantlable = false;
  private boolean active = true;
}
