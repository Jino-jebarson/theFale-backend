package com.thefale.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet_transactions")
@EntityListeners(com.thefale.service.WalletDocumentNumberListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(nullable = false, length = 50)
    private String type; // topup, escrow_hold, disbursement, credit_purchase, standby_credit, refund

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "platform_fee_amount", precision = 12, scale = 2)
    private BigDecimal platformFeeAmount;

    @Column(name = "platform_fee_gst_amount", precision = 12, scale = 2)
    private BigDecimal platformFeeGstAmount;

    @Column(name = "balance_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false, length = 30)
    private String status; // Completed, In Escrow, Processing, Refunded, Pending Reconciliation

    @Column(name = "ref_number", nullable = false, unique = true, length = 50)
    private String refNumber;

    @Column(unique = true, length = 50)
    private String utr;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 255)
    private String subtitle;

    @Column(name = "invoice_id", length = 50)
    private String invoiceId;

    @Column(name = "claim_id") private UUID claimId;
    @Column(name = "document_number", length = 64) private String documentNumber;
    @Column(name = "reviewed_by") private UUID reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "review_notes", columnDefinition = "TEXT") private String reviewNotes;
    @Column(name = "destination_snapshot", length = 255) private String destinationSnapshot;
    @Column(name = "bank_disbursed_at") private Instant bankDisbursedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
