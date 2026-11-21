package com.thefale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ReferralRewardPaymentRequest {
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9-]{6,50}$", message = "Enter the actual 6-50 character payment reference")
    private String paymentReference;
}
