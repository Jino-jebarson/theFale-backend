package com.thefale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateProfileDto {

    private UUID id;
    private UUID userId;
    private String name;
    private String email;
    private String phone;
    private String gender;
    private LocalDate dob;
    private String city;
    private String state;
    private String pincode;
    private String addressLine;

    // Student & level indicators
    private String candidateLevel; // "STUDENT", "FRESHER", "PROFESSIONAL"

    @com.fasterxml.jackson.annotation.JsonProperty("isStudent")
    private boolean isStudent;

    private String course;
    private String college;
    private String studyYear;
    private String universityRegNo;
    private Integer expectedGraduationYear;

    // Graduate indicators
    private String profession;
    private String specialty;
    private List<String> subSpecialties;
    private String qualification;
    private String council;
    private String councilState;
    private String regNumber;
    private String regYear;
    private String experienceYears;
    private String bio;

    // Compensation & preferences
    private Long expectedSalaryMinPaise;
    private Long expectedSalaryMaxPaise;
    private String currentSalary;
    private String noticePeriod;
    private String employmentType;
    private String availability;
    private List<String> skills;
    private List<String> languages;
    private List<String> preferredCities;
    private Boolean openToWork;
    private Boolean relocate;
    private String kycStatus; // PENDING, VERIFIED, REJECTED
    private String kycNotes;
    private Boolean isMentor;
    private String mentorStatus;
    private Instant verifiedAt;
    private Instant createdAt;

    // Associated sub-resources
    private List<CandidateDocumentDto> documents;
    private CandidatePayoutMethodDto payoutMethod;
    private StudentUpgradeRequestDto activeUpgradeRequest;
}
