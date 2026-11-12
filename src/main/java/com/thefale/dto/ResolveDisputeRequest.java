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
public class ResolveDisputeRequest {
    /**
     * Supported resolutions: RELEASE_TO_CANDIDATE, REFUND_HOSPITAL, PARTIAL_SPLIT
     */
    @NotBlank(message = "Resolution action is required")
    private String resolution;

    /**
     * Required when resolution is PARTIAL_SPLIT
     */
    private Integer approvedHours;

    private String notes;
}

