package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "candidate_payout_methods")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidatePayoutMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "candidate_id", nullable = false, unique = true)
    private UUID candidateId;

    @Column(name = "method_type", nullable = false, length = 20)
    private String methodType; // 'upi' or 'bank'

    @Column(name = "upi_id", length = 100)
    private String upiId;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "account_number_encrypted", length = 255)
    @Convert(converter = com.thefale.security.BankAccountConverter.class)
    private String accountNumberEncrypted;

    @Column(name = "account_number_last4", length = 4)
    private String accountNumberLast4;

    @Column(length = 11)
    private String ifsc;

    @Column(name = "account_holder_name", nullable = false, length = 100)
    private String accountHolderName;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "Pending"; // 'Verified', 'Pending'

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
