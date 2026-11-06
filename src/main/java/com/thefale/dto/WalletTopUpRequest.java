package com.thefale.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTopUpRequest {

    @NotNull(message = "Deposit amount in paise is required")
    @Min(value = 100000, message = "Minimum deposit amount is ₹1,000 (100,000 paise)")
    private Long amountPaise;

    @NotBlank(message = "Payment method is required (upi, netbanking, neft)")
    private String paymentMethod;

    @NotBlank(message = "UTR / Transaction Reference number is required for manual settlement")
    @Pattern(regexp = "^[A-Za-z0-9]{8,30}$", message = "UTR must be between 8 and 30 alphanumeric characters")
    private String utr;
}
