package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "courses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, length = 30)
    private String duration;

    @Builder.Default
    @Column(name = "cme_points", nullable = false)
    private int cmePoints = 0;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Builder.Default
    @Column(name = "for_students", nullable = false)
    private boolean forStudents = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
