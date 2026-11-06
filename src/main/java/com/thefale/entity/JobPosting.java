package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_postings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 50)
    private String department;

    @Column(name = "employment_type", nullable = false, length = 50)
    private String employmentType;

    @Builder.Default
    @Column(name = "is_internship", nullable = false)
    private boolean internship = false;

    @Column(nullable = false, length = 50)
    private String city;

    @Column(nullable = false)
    private int openings;

    @Column(name = "salary_min", nullable = false, precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", nullable = false, precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column(name = "experience_required", length = 50)
    private String experienceRequired;

    @Column(name = "qualification_required", length = 100)
    private String qualificationRequired;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "PENDING_APPROVAL"; // PENDING_APPROVAL, OPEN, CLOSED, REJECTED

    @Column(name = "moderated_by")
    private UUID moderatedBy;

    @Column(name = "moderation_notes", columnDefinition = "TEXT")
    private String moderationNotes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
