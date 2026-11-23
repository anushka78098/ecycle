package com.ecycle.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "rewards")
@Getter @Setter
public class Reward {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String code;
  private String title;
  private String description;
  private int costPoints;
  private Integer stock;
  private boolean active = true;
}
