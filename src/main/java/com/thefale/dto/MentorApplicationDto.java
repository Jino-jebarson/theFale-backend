package com.thefale.dto;

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
public class MentorApplicationDto {
    private UUID id;
    private UUID candidateId;
    private UUID userId;
    private String name;
    private String profession;
    private String specialty;
    private int yearsExperience;
    private String organization;
    private String status; // PENDING, APPROVED, REJECTED
    private boolean verified;
    private Instant appliedAt;
    private Instant reviewedAt;
    private String adminNotes;
}

