package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "huddle_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HuddleSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 100)
    private String category;

    @Builder.Default
    @Column(name = "host_type", nullable = false, length = 50)
    private String hostType = "mentor";

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Column(name = "host_name", nullable = false)
    private String hostName;

    @Column(name = "host_sub")
    private String hostSub;

    @Column(name = "session_when", nullable = false)
    private Instant sessionWhen;

    @Column(name = "session_where", nullable = false)
    private String sessionWhere;

    @Builder.Default
    @Column(nullable = false)
    private int seats = 30;

    @Builder.Default
    @Column(name = "booked_count", nullable = false)
    private int bookedCount = 0;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "for_roles")
    private String forRoles;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}

