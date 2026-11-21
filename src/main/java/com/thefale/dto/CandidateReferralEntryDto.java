package com.thefale.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data @Builder
public class CandidateReferralEntryDto {
    private UUID id;
    private String candidateName;
    private String status;
    private String rewardType;
    private BigDecimal rewardAmount;
    private String rewardStatus;
    private String rewardReason;
    private Instant joinedAt;
    private Instant qualifiedAt;
    private Instant paidAt;
}
