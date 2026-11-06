package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "minimum_role_rates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinimumRoleRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String profession;

    @Column(name = "min_rate_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal minRatePerHour;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
