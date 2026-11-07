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
public class JobApplicationDto {

    private UUID id;
    private UUID jobId;
    private String jobTitle;
    private UUID candidateId;
    private String candidateName;
    private String facilityName;
    private String department;
    private String city;
    private String status;
    private int matchScore;
    private Instant appliedAt;
    private Instant interviewScheduledAt;
    private String interviewLocationOrLink;
    private String withdrawalReason;
    private String candidateRole;
    private String candidateSpecialty;
    private String candidateCity;
    private String candidateExperience;
    private String candidatePhone;
    private String candidateEmail;
    private boolean councilVerified;
    private String regNumber;
    private String candidateQualification;
    private String candidateCouncil;
    private String candidateState;
    private String candidateBio;
    private String candidateNoticePeriod;
    private Long expectedSalaryMinPaise;
    private Long expectedSalaryMaxPaise;
    private String resumeUrl;
    private String resumeFileName;
    private java.util.List<CandidateDocumentDto> candidateDocuments;

    private Integer actualHours;
    private Integer scheduledHours;
    private Long hourlyRatePaise;

    public String getPhone() {
        return candidatePhone;
    }

    public String getEmail() {
        return candidateEmail;
    }
}
