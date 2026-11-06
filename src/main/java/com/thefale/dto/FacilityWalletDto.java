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
public class FacilityWalletDto {
    private UUID id;
    private UUID facilityId;
    private long availableBalancePaise;
    private long lockedEscrowBalancePaise;
    private long totalDisbursedPaise;
    private int candidateCredits;
    private Instant updatedAt;
}
