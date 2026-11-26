package com.ecycle.backend.entity;

import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "certificates")
@Getter @Setter
public class Certificate {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String certificateNo;
  @Enumerated(EnumType.STRING)
  private CertificateType type;
  private String verificationCode;
  private Long listingId;
  private Long pickupId;
  private Long sanitizationRecordId;
  private String passportCode;
  private Long holderId;
  private Long issuedBy;
  private String payload;
  private String contentHash;
  private String signature;
  private Integer custodySeq;
  private String custodyHeadHash;
  private Instant issuedAt;
  @PrePersist void prePersist() { if (issuedAt == null) issuedAt = Instant.now(); }
}
