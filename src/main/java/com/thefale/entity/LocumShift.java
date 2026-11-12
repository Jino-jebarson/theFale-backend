package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "locum_shifts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocumShift {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 50)
    private String department;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(nullable = false)
    private int hours;

    @Column(name = "rate_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal ratePerHour;

    @Builder.Default
    @Column(name = "surge_rate_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal surgeRatePerHour = BigDecimal.ZERO;

    @Column(name = "surge_label", length = 50)
    private String surgeLabel;

    @Column(nullable = false)
    private int openings;

    @Builder.Default
    @Column(name = "slots_claimed", nullable = false)
    private int slotsClaimed = 0;

    @Builder.Default
    @Column(name = "is_urgent", nullable = false)
    private boolean urgent = false;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "OPEN"; // PENDING_APPROVAL, OPEN, FILLED, CANCELLED, REJECTED, COMPLETED, EXPIRED

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "facility_fee_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal facilityFeePercent = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "facility_fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal facilityFeeAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "auto_approved", nullable = false)
    private boolean autoApproved = false;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
