package com.thefale.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPayoutApprovalRequest {

    @NotBlank(message = "Banking UTR / Reference number is mandatory for payout disbursement")
    private String utr;

    private String notes;
}
