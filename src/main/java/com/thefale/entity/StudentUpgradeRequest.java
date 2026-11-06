package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student_upgrade_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentUpgradeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(nullable = false, length = 100)
    private String qualification;

    @Column(nullable = false, length = 100)
    private String council;

    @Column(name = "reg_number", nullable = false, length = 50)
    private String regNumber;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "PENDING"; // 'PENDING', 'APPROVED', 'REJECTED'

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
