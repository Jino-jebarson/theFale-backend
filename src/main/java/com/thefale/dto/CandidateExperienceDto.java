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
public class CandidateExperienceDto {
    private String id;
    @NotBlank
    @Size(max = 150)
    private String organization;
    @NotBlank
    @Size(max = 120)
    private String title;
    @Size(max = 7)
    private String startDate;
    @Size(max = 7)
    private String endDate;
    private boolean current;
    @Size(max = 2000)
    private String description;
}
