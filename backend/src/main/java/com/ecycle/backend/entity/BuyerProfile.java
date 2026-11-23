package com.ecycle.backend.entity;

import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "buyer_profiles")
@Getter @Setter
public class BuyerProfile {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long userId;
  private String organization;
  @Enumerated(EnumType.STRING)
  private BuyerType buyerType = BuyerType.INDIVIDUAL;
  private String city;
  private boolean verified = false;
  private int recoveryCapability = 0;
  private int environmentalRating = 0;
}
