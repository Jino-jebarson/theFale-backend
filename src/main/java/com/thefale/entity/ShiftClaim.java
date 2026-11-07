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

    @Column(name = "actual_hours")
    private Integer actualHours;

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
