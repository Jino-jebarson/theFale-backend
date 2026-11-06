package com.thefale.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawApplicationRequest {

    @Size(max = 255, message = "Withdrawal reason must not exceed 255 characters")
    private String reason;
}
