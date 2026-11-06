package com.thefale.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PostShiftRequest {

    @NotBlank(message = "Shift title is required")
    private String title;

    @NotBlank(message = "Department is required")
    private String department;

    @NotNull(message = "Start time is required")
    private Instant startAt;

    @NotNull(message = "End time is required")
    private Instant endAt;

    private Integer hours;

    private String description;

    @NotNull(message = "Hourly base rate in paise is required")
    @Min(value = 10000, message = "Hourly rate must be at least ₹100/hr (10,000 paise)")
    private Long ratePerHourPaise;

    @Builder.Default
    private Long surgeRatePerHourPaise = 0L;

    private String surgeLabel;

    @NotNull(message = "Openings must be at least 1")
    @Min(value = 1, message = "Openings must be at least 1")
    private Integer openings;

    @JsonProperty("urgent")
    @JsonAlias({"urgent", "isUrgent"})
    private boolean urgent;
}
