package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "candidate_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String gender;

    private LocalDate dob;

    @Column(nullable = false, length = 50)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(length = 10)
    private String pincode;

    @Column(name = "address_line", columnDefinition = "TEXT")
    private String addressLine;

    // Student fields
    @Column(length = 100)
    private String course;

    @Column(length = 150)
    private String college;

    @Column(name = "study_year", length = 30)
    private String studyYear;

    @Column(name = "university_reg_no", length = 50)
    private String universityRegNo;

    @Column(name = "expected_graduation_year")
    private Integer expectedGraduationYear;

    // Graduate fields
    @Column(length = 50)
    private String profession;

    @Column(length = 100)
    private String specialty;

    @Column(length = 100)
    private String qualification;

    @Column(length = 100)
    private String council;

    @Column(name = "council_state", length = 100)
    private String councilState;

    @Column(name = "reg_number", length = 50)
    private String regNumber;

    @Column(name = "reg_year", length = 10)
    private String regYear;

    @Column(name = "experience_years", length = 30)
    private String experienceYears;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "sub_specialties", columnDefinition = "TEXT")
    private String subSpecialties;

    @Column(name = "employment_type", length = 100)
    private String employmentType;

    @Column(length = 100)
    private String availability;

    @Column(name = "current_salary", length = 50)
    private String currentSalary;

    @Column(columnDefinition = "TEXT")
    private String skills;

    @Column(columnDefinition = "TEXT")
    private String languages;

    @Column(name = "preferred_cities", columnDefinition = "TEXT")
    private String preferredCities;

    @Builder.Default
    @Column(name = "open_to_work")
    private Boolean openToWork = true;

    @Builder.Default
    @Column(name = "relocate")
    private Boolean relocate = false;

    @Column(name = "expected_salary_min_paise")
    private Long expectedSalaryMinPaise;

    @Column(name = "expected_salary_max_paise")
    private Long expectedSalaryMaxPaise;

    @Column(name = "notice_period", length = 50)
    private String noticePeriod;

    @Builder.Default
    @Column(name = "kyc_status", nullable = false, length = 30)
    private String kycStatus = "PENDING"; // PENDING, VERIFIED, REJECTED

    @Builder.Default
    @Column(name = "is_mentor", nullable = false)
    private Boolean isMentor = false;

    @Builder.Default
    @Column(name = "mentor_status", nullable = false, length = 30)
    private String mentorStatus = "NONE"; // NONE, PENDING, APPROVED, REJECTED

    @Column(name = "kyc_notes", columnDefinition = "TEXT")
    private String kycNotes;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Builder.Default
    @Column(name = "shift_rating_avg", nullable = false, precision = 3, scale = 2)
    private java.math.BigDecimal shiftRatingAvg = java.math.BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "shift_rating_count", nullable = false)
    private Integer shiftRatingCount = 0;

    @Builder.Default
    @Column(name = "completed_shifts_count", nullable = false)
    private Integer completedShiftsCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
