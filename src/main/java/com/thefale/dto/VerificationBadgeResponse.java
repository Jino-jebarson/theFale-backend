package com.thefale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationBadgeResponse {
    private UUID verificationId;
    private String entityType;
    private String maskedName;
    private boolean verified;
    private String accreditationText;
}
