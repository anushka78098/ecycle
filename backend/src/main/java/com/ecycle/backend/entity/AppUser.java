package com.ecycle.backend.entity;

import com.ecycle.shared.Enums;
import com.ecycle.shared.Enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter @Setter
public class AppUser {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String email;
  private String passwordHash;
  private String fullName;
  private String phone;
  @Enumerated(EnumType.STRING)
  private AccountStatus status = AccountStatus.ACTIVE;
  private int failedLogins = 0;
  private Instant lockedUntil;
  private String googleSub;
  private Instant createdAt;
  @PrePersist void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; }
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
  private Set<AppRole> roles = new HashSet<>();
  public Set<Enums.Role> roleSet() { Set<Enums.Role> r = new java.util.TreeSet<>(); roles.forEach(x -> r.add(Enums.Role.valueOf(x.getName()))); return r; }
  public boolean hasRole(Enums.Role r) { return roles.stream().anyMatch(x -> x.getName().equals(r.name())); }
}
