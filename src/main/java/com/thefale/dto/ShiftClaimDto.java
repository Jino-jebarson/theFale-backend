package com.thefale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftClaimDto {
    private UUID id;
    private UUID shiftId;
    private UUID candidateId;
    private String shiftTitle;
    private String facilityName;
    private String department;
    private Instant startAt;
    private Instant endAt;
    private int hours;
    private Integer actualHours;
    private String status;
    private long grossAmountPaise;
    private long platformFeePaise;
    private long tdsAmountPaise;
    private long netAmountPaise;
    private String payoutStatus;
    private String utr;
    private String payoutSlipRef;
    private Instant claimedAt;
    private Instant checkedInAt;
    private Instant completedAt;
    private int matchScore;
    private String candidateName;
    private String candidateRole;
    private String candidateSpecialty;
    private String candidateCity;
    private String candidateQualification;
    private String candidateExperience;
    private boolean councilVerified;
    private String facilityCity;
    private String rejectionReason;
    private double platformFeePercent;
    private boolean firstShiftFree;
    private String checkInPin;
    private String disputeReason;
    private Instant disputedAt;
    private Integer hospitalRating;
    private String hospitalReview;
    private String ratingTags;
    private Instant ratedAt;
    private Double candidateRatingAvg;
    private Integer candidateRatingCount;
    private Integer completedShiftsCount;
}
