package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "facility_wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacilityWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "facility_id", nullable = false, unique = true)
    private UUID facilityId;

    @Builder.Default
    @Column(name = "available_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "locked_escrow_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal lockedEscrowBalance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_disbursed", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDisbursed = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "candidate_credits", nullable = false)
    private int candidateCredits = 0;

    @Column(name = "first_completed_shift_id")
    private UUID firstCompletedShiftId;

    @Version
    @Column(nullable = false)
    private Long version;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
