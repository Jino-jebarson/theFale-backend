package com.thefale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApplyReferralRequest {
    @NotBlank(message = "Referral code is required")
    @Size(max = 12, message = "Referral code is invalid")
    private String referralCode;
}
