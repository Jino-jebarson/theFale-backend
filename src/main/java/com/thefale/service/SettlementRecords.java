package com.thefale.service;
import com.thefale.entity.*;
import com.thefale.exception.BadRequestException;
import com.thefale.repository.WalletTransactionRepository;
import java.math.BigDecimal;

/** A claim has one settlement document, advanced from queued to paid rather than billed twice. */
public final class SettlementRecords {
    private SettlementRecords() {}
    public static WalletTransaction save(WalletTransactionRepository repository, FacilityWallet wallet, ShiftClaim claim,
                                         LocumShift shift, BigDecimal total, BigDecimal fee, BigDecimal gst, boolean paid) {
        WalletTransaction record = repository.findByClaimIdAndType(claim.getId(), "disbursement").orElseGet(() ->
                WalletTransaction.builder().walletId(wallet.getId()).claimId(claim.getId()).type("disbursement")
                        .refNumber(DocumentReferences.settlement(claim.getId())).build());
        if ("Completed".equalsIgnoreCase(record.getStatus())) throw new BadRequestException("This settlement document has already been paid.");
        record.setAmount(total.negate()); record.setPlatformFeeAmount(fee); record.setPlatformFeeGstAmount(gst);
        record.setBalanceAfter(wallet.getAvailableBalance()); record.setStatus(paid ? "Completed" : "Processing");
        record.setTitle("Shift settlement: " + shift.getTitle());
        record.setSubtitle(paid ? "Bank disbursement recorded by admin. Reference: " + claim.getUtr() : "Timesheet approved. Awaiting bank disbursement; this is not proof of payment.");
        if (paid) { record.setUtr(claim.getUtr()); record.setDestinationSnapshot(claim.getPayoutMethodSnapshot()); record.setBankDisbursedAt(claim.getDisbursedAt()); }
        return repository.save(record);
    }
}
