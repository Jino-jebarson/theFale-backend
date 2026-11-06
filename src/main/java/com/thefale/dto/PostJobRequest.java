package com.thefale.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostJobRequest {

    @NotBlank(message = "Job title is required")
    private String title;

    @NotBlank(message = "Department is required")
    private String department;

    @NotBlank(message = "Employment type is required")
    private String employmentType;

    @JsonProperty("isInternship")
    private boolean internship;

    @NotBlank(message = "City is required")
    private String city;

    @Min(value = 1, message = "Openings must be at least 1")
    private int openings;

    @NotNull(message = "Minimum salary in paise is required")
    private Long salaryMinPaise;

    @NotNull(message = "Maximum salary in paise is required")
    private Long salaryMaxPaise;

    private String experienceRequired;
    private String qualificationRequired;

    @NotBlank(message = "Job description is required")
    private String description;
}
