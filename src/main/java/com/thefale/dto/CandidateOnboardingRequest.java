package com.thefale.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CandidateOnboardingRequest {

    @NotBlank(message = "Full name is required")
    @jakarta.validation.constraints.Size(max = 150, message = "Full name must not exceed 150 characters")
    @JsonAlias({"name", "candidateName"})
    private String fullName;

    private String email;
    private String gender;
    private String dob;

    @NotBlank(message = "City is required")
    @jakarta.validation.constraints.Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    private String state;
    private String pincode;
    private String addressLine;

    // "STUDENT", "FRESHER", "PROFESSIONAL"
    private String candidateLevel;

    // Student fields
    private String course;
    @JsonAlias({"college", "collegeName"})
    private String collegeName;
    private String studyYear; // 1st, 2nd, 3rd, 4th, Intern
    private String universityRegNo;
    private Integer expectedGraduationYear;

    // Graduate fields
    private String profession;
    @JsonAlias({"specialty", "primarySpecialty"})
    private String primarySpecialty;
    private List<String> subSpecialties;
    private String qualification;
    private String council;
    @JsonAlias({"regNumber", "registrationNumber", "councilRegistrationNumber"})
    private String councilRegistrationNumber;
    private String councilState;
    private String regYear;
    private String experienceYears;
    @jakarta.validation.constraints.Size(max = 2000, message = "Bio must not exceed 2000 characters")
    private String bio;

    // Salary & preferences
    private Long expectedSalaryMinPaise;
    private Long expectedSalaryMaxPaise;
    private Long expectedSalaryMinRupees;
    private Long expectedSalaryMaxRupees;
    private String currentSalary;
    private String noticePeriod;
    private String employmentType;
    private String availability;
    private Boolean openToWork;
    private Boolean relocate;
    private List<String> preferredCities;
    private List<String> skills;
    private List<String> languages;

    public Long getExpectedSalaryMinPaise() {
        if (expectedSalaryMinPaise != null) {
            return expectedSalaryMinPaise;
        }
        if (expectedSalaryMinRupees != null) {
            return expectedSalaryMinRupees * 100L;
        }
        return null;
    }

    public Long getExpectedSalaryMaxPaise() {
        if (expectedSalaryMaxPaise != null) {
            return expectedSalaryMaxPaise;
        }
        if (expectedSalaryMaxRupees != null) {
            return expectedSalaryMaxRupees * 100L;
        }
        return null;
    }

    @JsonSetter("preferredCities")
    public void setPreferredCities(JsonNode node) {
        this.preferredCities = parseStringOrArray(node);
    }

    @JsonSetter("skills")
    public void setSkills(JsonNode node) {
        this.skills = parseStringOrArray(node);
    }

    @JsonSetter("languages")
    public void setLanguages(JsonNode node) {
        this.languages = parseStringOrArray(node);
    }

    @JsonSetter("subSpecialties")
    public void setSubSpecialties(JsonNode node) {
        this.subSpecialties = parseStringOrArray(node);
    }

    private List<String> parseStringOrArray(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            List<String> list = new ArrayList<>();
            node.forEach(item -> list.add(item.asText()));
            return list;
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (text.isEmpty()) {
                return Collections.emptyList();
            }
            return Arrays.stream(text.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        return Collections.emptyList();
    }

    // Proofs / URLs & dynamic file names
    private String identityProofType; // AADHAAR, PAN, PASSPORT, VOTER_ID
    private String identityProofNumber;
    private String profilePhotoUrl;
    private String profilePhotoFileName;
    private String resumeUrl;
    private String resumeFileName;
    private String registrationCertificateUrl;
    private String registrationCertificateFileName;
    private String qualificationCertificateUrl;
    private String qualificationCertificateFileName;
    private String identityProofUrl;
    private String identityProofFileName;
    private String bonafideCertificateUrl;
    private String bonafideCertificateFileName;
    private String studentIdCardUrl;
    private String studentIdCardFileName;
}
