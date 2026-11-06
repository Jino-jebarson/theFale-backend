package com.thefale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminMfaVerifyRequest {

    @NotBlank(message = "Temporary MFA token is required")
    private String tempToken;

    @NotBlank(message = "MFA code is required")
    @Pattern(regexp = "^\\d{6}$", message = "MFA code must be 6 digits")
    private String totpCode;
}
