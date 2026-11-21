package com.thefale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateEducationUpdateRequest {
    @NotBlank(message = "Confirm the qualification found on your certificate.")
    @Size(max = 160, message = "Qualification must be 160 characters or fewer.")
    private String qualification;

    @Size(max = 200, message = "College or institution must be 200 characters or fewer.")
    private String collegeName;
}
