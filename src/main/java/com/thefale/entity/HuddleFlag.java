package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "huddle_flags")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HuddleFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "reported_by", nullable = false)
    private UUID reportedBy;

    @Column(nullable = false, length = 500)
    private String reason;

    @Builder.Default
    @Column(nullable = false)
    private boolean resolved = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
