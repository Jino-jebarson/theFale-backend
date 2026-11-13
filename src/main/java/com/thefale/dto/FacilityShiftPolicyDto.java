package com.thefale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacilityShiftPolicyDto {
    private boolean hasLinkedBank;
    private boolean kycVerified;
    private boolean firstShiftApprovalRequired;
    private boolean firstShiftFeeWaived;
    private double platformFeePercent;
    private double platformFeeGstPercent;
    private long priorApprovedShiftsCount;
    private long priorValidShiftsCount;
    private long availableBalancePaise;
}

