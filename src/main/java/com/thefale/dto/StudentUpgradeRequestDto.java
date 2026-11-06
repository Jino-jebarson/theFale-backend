package com.thefale.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentUpgradeRequestDto {

    private UUID id;

    @NotBlank(message = "Qualification is required (e.g. MBBS, BDS, B.Sc Nursing)")
    private String qualification;

    @NotBlank(message = "State medical or nursing council is required")
    private String council;

    @NotBlank(message = "Council registration number is required")
    private String regNumber;

    private String degreeCertificateUrl;
    private String status; // PENDING, APPROVED, REJECTED
    private String adminNotes;
    private Instant createdAt;
    private Instant reviewedAt;
}
