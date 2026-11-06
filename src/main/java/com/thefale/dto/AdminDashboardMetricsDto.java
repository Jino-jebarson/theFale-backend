package com.thefale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardMetricsDto {
    private long pendingCandidateKycCount;
    private long pendingFacilityKycCount;
    private long pendingPostingsCount;
    private long pendingApplicationsCount;
    private long pendingShiftPayoutsCount;
    private long flaggedHuddlePostsCount;
    private long studentUpgradeRequestsCount;

    // Total counts
    private long totalCandidatesCount;
    private long verifiedCandidatesCount;
    private long totalFacilitiesCount;
    private long verifiedFacilitiesCount;
    private long totalPostingsCount;
    private long activePostingsCount;
    private long totalApplicationsCount;
    private long shortlistedApplicationsCount;

    // SLA & Analytics
    private double slaTurnaroundHours;
    private double slaOnTimePercentage;

    // Cadre breakdown (profession -> count)
    private Map<String, Long> cadreBreakdown;
}
