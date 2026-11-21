package com.thefale.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data @Builder
public class CandidateReferralDashboardDto {
    private String referralCode;
    private int firstShiftRewardRupees;
    private int hireRewardRupees;
    private int monthlyRewardLimit;
    private long invitedCount;
    private BigDecimal earnedAmount;
    private BigDecimal paidAmount;
    private BigDecimal dueAmount;
    private List<CandidateReferralEntryDto> referrals;
}
