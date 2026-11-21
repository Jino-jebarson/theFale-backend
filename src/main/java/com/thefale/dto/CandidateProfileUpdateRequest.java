package com.thefale.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CandidateProfileUpdateRequest {
    @Size(max = 150)
    private String fullName;
    private String email;
    private String gender;
    private String dob;
    @Size(max = 100)
    private String city;
    @Size(max = 100)
    private String locality;
    private String state;
    private String pincode;
    private String addressLine;

    private String course;
    @JsonAlias({"college", "collegeName"})
    private String collegeName;
    private String studyYear;
    private String universityRegNo;
    private Integer expectedGraduationYear;
    private String expectedGraduationMonth;

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
    @Size(max = 30)
    private List<@Valid CandidateExperienceDto> experienceHistory;
    @Size(max = 30)
    private List<@Valid CandidateCertificationDto> certifications;
    @Size(max = 2000)
    private String bio;

    private Long expectedSalaryMinPaise;
    private Long expectedSalaryMaxPaise;
    private Long expectedSalaryMinRupees;
    private Long expectedSalaryMaxRupees;
    private Boolean updateExpectedSalary;
    private String currentSalary;
    private String noticePeriod;
    private String employmentType;
    private String availability;
    private Boolean openToWork;
    private Boolean relocate;
    private List<String> preferredCities;
    private List<String> skills;
    private List<String> languages;
}
