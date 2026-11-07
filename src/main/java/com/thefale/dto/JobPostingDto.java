package com.thefale.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class JobPostingDto {

    private UUID id;
    private UUID facilityId;
    private String facilityName;
    private String facilityCity;
    private String facilityAccreditation;
    private String title;
    private String department;
    private String employmentType;

    @JsonProperty("isInternship")
    private boolean internship;

    private String city;
    private String workLocation;
    private int openings;
    private long salaryMinPaise;
    private long salaryMaxPaise;
    private String salaryDisplay;
    private String experienceRequired;
    private String qualificationRequired;
    private String description;
    private String status;
    private Instant postedAt;
    private Instant createdAt;

    @JsonProperty("hasApplied")
    private boolean hasApplied;

    @JsonProperty("isSaved")
    private boolean isSaved;
}
