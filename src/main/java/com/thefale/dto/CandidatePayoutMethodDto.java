package com.thefale.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidatePayoutMethodDto {

    @NotBlank(message = "Method type is required ('upi' or 'bank')")
    private String methodType; // 'upi' or 'bank'

    private String upiId;
    private String bankName;
    private String accountNumber;
    private String accountNumberLast4;
    private String ifsc;

    @NotBlank(message = "Account holder name is required")
    private String accountHolderName;

    private String status; // 'Verified', 'Pending'
    private Instant verifiedAt;
}
