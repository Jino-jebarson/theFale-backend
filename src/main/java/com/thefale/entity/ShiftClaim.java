package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shift_claims")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "CLAIMED"; // CLAIMED, CHECKED_IN, COMPLETED, NO_SHOW, CANCELLED, DISPUTED

    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount;

    @Builder.Default
    @Column(name = "platform_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal platformFee = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "tds_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal tdsAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;

    @Builder.Default
    @Column(name = "payout_status", nullable = false, length = 30)
    private String payoutStatus = "IN_ESCROW"; // IN_ESCROW, PENDING_DISBURSEMENT, DISBURSED, REFUNDED

    @Column(length = 50)
    private String utr;

    @Column(name = "payout_slip_ref", length = 50)
    private String payoutSlipRef;

    @Column(name = "payout_method_snapshot", length = 255) private String payoutMethodSnapshot;
    @Column(name = "payout_candidate_name", length = 100) private String payoutCandidateName;
    @Column(name = "payout_registration_number", length = 100) private String payoutRegistrationNumber;

    @Column(name = "actual_hours")
    private Integer actualHours;

    @Column(name = "check_in_pin", length = 6)
    private String checkInPin;

    @Column(name = "dispute_reason", columnDefinition = "TEXT")
    private String disputeReason;

    @Column(name = "disputed_at")
    private Instant disputedAt;

    @Column(name = "disputed_by", length = 30)
    private String disputedBy;

    @Builder.Default
    @Column(name = "platform_fee_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal platformFeePercent = BigDecimal.ZERO;

    @Column(name = "hospital_rating")
    private Integer hospitalRating;

    @Column(name = "hospital_review", columnDefinition = "TEXT")
    private String hospitalReview;

    @Column(name = "rating_tags", columnDefinition = "TEXT")
    private String ratingTags;

    @Column(name = "rated_at")
    private Instant ratedAt;

    private Double checkInLatitude;
    private Double checkInLongitude;
    private Double checkInAccuracy;
    private Instant locationCapturedAt;
    private Instant selfieCapturedAt;
    @Column(columnDefinition = "TEXT")
    private String checkInSelfie;
    private String checkInMethod;
    @Column(length = 64)
    private String checkInQrToken;
    private int checkInFailedAttempts;
    private Instant checkInBlockedUntil;
    @Column(precision = 12, scale = 2)
    private BigDecimal hospitalFeeAmount;

    @Column(name = "hospital_fee_gst_amount", precision = 12, scale = 2)
    private BigDecimal hospitalFeeGstAmount;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "claimed_at", nullable = false, updatable = false)
    private Instant claimedAt;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;
}
