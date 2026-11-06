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
public class AdminApplicationScreeningRequest {

    @NotBlank(message = "Action is required")
    @Pattern(regexp = "^(SHORTLIST|APPROVE|REJECT)$", message = "Action must be SHORTLIST, APPROVE, or REJECT")
    private String action;

    private String notes;
}
