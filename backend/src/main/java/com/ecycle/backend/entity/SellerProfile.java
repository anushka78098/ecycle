package com.ecycle.backend.entity;

import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "seller_profiles")
@Getter @Setter
public class SellerProfile {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long userId;
  private String businessName;
  private String address;
  private String city;
  @Enumerated(EnumType.STRING)
  private VerificationStatus verificationStatus = VerificationStatus.PENDING;
  private Instant verifiedAt;
  private BigDecimal trustScore = new BigDecimal("50");
}
