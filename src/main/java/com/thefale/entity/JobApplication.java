package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_applications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "PENDING_SCREENING"; // PENDING_SCREENING, SHORTLISTED, INTERVIEW, OFFERED, HIRED, REJECTED, WITHDRAWN

    @Builder.Default
    @Column(name = "match_score", nullable = false)
    private int matchScore = 75;

    @Column(name = "withdrawal_reason")
    private String withdrawalReason;

    @Column(name = "interview_scheduled_at")
    private Instant interviewScheduledAt;

    @Column(name = "interview_location_or_link")
    private String interviewLocationOrLink;

    @CreationTimestamp
    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
