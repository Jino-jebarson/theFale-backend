package com.thefale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransactionDto {
    private UUID id;
    private UUID walletId;
    private String type;
    private long amountPaise;
    private Long platformFeePaise;
    private Long platformFeeGstPaise;
    private long balanceAfterPaise;
    private String status;
    private String refNumber;
    private String utr;
    private String title;
    private String subtitle;
    private String invoiceId;
    private UUID claimId;
    private Instant reviewedAt;
    private String reviewNotes;
    private boolean canonicalDocument;
    private String destinationSnapshot;
    private Instant bankDisbursedAt;
    private Instant createdAt;
}
