package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "facility_team_invites", uniqueConstraints = @UniqueConstraint(name = "uq_facility_team_invite_phone", columnNames = {"facility_id", "phone"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacilityTeamInvite {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 15)
    private String phone;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "INVITED";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
