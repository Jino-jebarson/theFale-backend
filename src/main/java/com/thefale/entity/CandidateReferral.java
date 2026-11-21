package com.thefale.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "candidate_referrals", uniqueConstraints = @UniqueConstraint(name = "uk_candidate_referral_referred", columnNames = "referred_candidate_id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CandidateReferral {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "referrer_candidate_id", nullable = false)
    private UUID referrerCandidateId;

    @Column(name = "referred_candidate_id", nullable = false, unique = true)
    private UUID referredCandidateId;

    @Column(name = "referral_code", nullable = false, length = 12)
    private String referralCode;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "JOINED";

    @Column(name = "reward_type", length = 20)
    private String rewardType;

    @Column(name = "reward_amount", precision = 10, scale = 2)
    private BigDecimal rewardAmount;

    @Builder.Default
    @Column(name = "reward_status", nullable = false, length = 20)
    private String rewardStatus = "NONE";

    @Column(name = "reward_reason", columnDefinition = "TEXT")
    private String rewardReason;

    @Column(name = "qualified_at")
    private Instant qualifiedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "payment_reference", length = 50)
    private String paymentReference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
