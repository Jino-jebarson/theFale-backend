package com.thefale.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "candidate_referral_codes")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CandidateReferralCode {
    @Id
    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "referral_code", nullable = false, unique = true, length = 12)
    private String referralCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
