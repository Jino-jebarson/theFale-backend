package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hospital_bank_accounts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HospitalBankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "facility_id", nullable = false, unique = true)
    private UUID facilityId;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "account_number_encrypted", nullable = false, length = 255)
    @Convert(converter = com.thefale.security.BankAccountConverter.class)
    private String accountNumberEncrypted;

    @Column(name = "account_number_last4", nullable = false, length = 4)
    private String accountNumberLast4;

    @Column(nullable = false, length = 11)
    private String ifsc;

    @Column(length = 100)
    private String branch;

    @Column(name = "account_holder_name", nullable = false, length = 100)
    private String accountHolderName;

    @Builder.Default
    @Column(name = "account_type", nullable = false, length = 50)
    private String accountType = "Current";

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "Pending Penny Drop"; // Verified, Pending Penny Drop, Under Review

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
