package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "platform_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformSetting {

    @Id
    @Column(nullable = false, length = 50)
    private String key;

    @Column(nullable = false, length = 255)
    private String value;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
