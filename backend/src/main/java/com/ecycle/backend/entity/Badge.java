package com.ecycle.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "badges")
@Getter @Setter
public class Badge {
  @Id
  private String code;
  private String name;
  private String description;
  private String criteria;
  private BigDecimal threshold;
  private String icon;
  private int sortOrder;
}
