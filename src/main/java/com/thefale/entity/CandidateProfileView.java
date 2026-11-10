package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;


@Entity
@Table(name = "candidate_profile_views", indexes = {
        @Index(name = "idx_candidate_views_cid_date", columnList = "candidate_id, viewed_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateProfileView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "facility_id")
    private UUID facilityId;

    @Column(name = "facility_name")
    private String facilityName;

    @CreationTimestamp
    @Column(name = "viewed_at", nullable = false, updatable = false)
    private OffsetDateTime viewedAt;
}
