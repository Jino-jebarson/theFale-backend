package com.thefale.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateHuddleSessionRequest {

    @NotBlank(message = "Session title is required")
    private String title;

    @NotBlank(message = "Category is required")
    private String category;

    @NotNull(message = "Session date and time is required")
    private Instant when;

    @NotBlank(message = "Session location / virtual link is required")
    private String where;

    @Min(value = 1, message = "Seats must be at least 1")
    @Max(value = 500, message = "Seats cannot exceed 500")
    @Builder.Default
    private Integer seats = 30;

    private String description;

    private List<String> forRoles;
}

