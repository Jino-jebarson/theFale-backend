package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 20, unique = true)
    private String phone;

    @Column(length = 150, unique = true)
    private String email;

    @Column(length = 100)
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @Builder.Default
    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;

    @Column(name = "mfa_secret")
    private String mfaSecret;

    @Column(nullable = false, length = 30)
    private String role; // ROLE_CANDIDATE, ROLE_FACILITY, ROLE_ADMIN

    @Column(name = "candidate_level", length = 30)
    private String candidateLevel; // STUDENT, FRESHER, PROFESSIONAL

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "ACTIVE"; // ACTIVE, SUSPENDED, LOCKED

    @Builder.Default
    @Column(name = "is_onboarded", nullable = false)
    private boolean isOnboarded = false;

    @Builder.Default
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
