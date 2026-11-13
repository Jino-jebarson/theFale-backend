package com.thefale.service;
import com.thefale.dto.*;
import com.thefale.entity.*;
import com.thefale.exception.*;
import com.thefale.repository.*;
import com.thefale.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class FinanceReviewService {
    private final WalletTransactionRepository transactions;
    private final FacilityWalletRepository wallets;
    private final FacilityProfileRepository facilities;
    private final CandidateProfileRepository candidates;
    private final HospitalBankAccountRepository hospitalBanks;
    private final CandidatePayoutMethodRepository candidateBanks;
    private final AuditLogService audit;

    @Transactional(readOnly=true)
    public List<Map<String,Object>> pendingTopUps() {
        return transactions.findByTypeAndStatusOrderByCreatedAtAsc("topup", "Pending Reconciliation").stream().map(t -> {
            var wallet = wallets.findById(t.getWalletId()).orElseThrow();
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id",t.getId()); row.put("amountPaise",t.getAmount().movePointRight(2).longValueExact());
            row.put("utr",t.getUtr()); row.put("createdAt",t.getCreatedAt()); row.put("documentNumber",t.getDocumentNumber());
            row.put("facilityName",facilities.findById(wallet.getFacilityId()).map(FacilityProfile::getHospitalName).orElse("Hospital"));
            return row;
        }).toList();
    }
    @Transactional
    public void reviewTopUp(CustomUserPrincipal actor, UUID id, TopUpReviewRequest request) {
        WalletTransaction row = transactions.findByIdWithLock(id).orElseThrow(() -> new ResourceNotFoundException("Deposit request not found"));
        if (!"topup".equals(row.getType()) || !"Pending Reconciliation".equals(row.getStatus())) throw new BadRequestException("This deposit is no longer awaiting reconciliation.");
        if (!row.getUtr().equalsIgnoreCase(request.getBankReference().trim())) throw new BadRequestException("Bank reference must match the submitted deposit UTR.");
        FacilityWallet wallet = wallets.findByIdWithLock(row.getWalletId()).orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        if (request.isApproved()) wallet.setAvailableBalance(wallet.getAvailableBalance().add(row.getAmount()));
        wallets.save(wallet);
        row.setStatus(request.isApproved() ? "Completed" : "Rejected"); row.setBalanceAfter(wallet.getAvailableBalance());
        row.setReviewedBy(actor.getId()); row.setReviewedAt(Instant.now()); row.setReviewNotes(request.getNotes().trim()); transactions.save(row);
        audit.logEvent(actor.getId(), request.isApproved() ? "DEPOSIT_RECONCILED" : "DEPOSIT_REJECTED", "WalletTransaction", id.toString(), Map.of("notes", request.getNotes().trim()));
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> pendingAccounts() {
        List<Map<String,Object>> result = new ArrayList<>();
        hospitalBanks.findAll().stream().filter(b -> (!"Verified".equalsIgnoreCase(b.getStatus()) || b.getVerifiedAt() == null)).forEach(b -> {
            Map<String,Object> row = new LinkedHashMap<>(); row.put("id",b.getId()); row.put("kind","hospital");
            row.put("name",facilities.findById(b.getFacilityId()).map(FacilityProfile::getHospitalName).orElse("Hospital"));
            row.put("holder",b.getAccountHolderName()); row.put("destination",b.getBankName()+" ****"+b.getAccountNumberLast4()); row.put("updatedAt",b.getUpdatedAt()); result.add(row);
        });
        candidateBanks.findAll().stream().filter(b -> (!"Verified".equalsIgnoreCase(b.getStatus()) || b.getVerifiedAt() == null)).forEach(b -> {
            Map<String,Object> row = new LinkedHashMap<>(); row.put("id",b.getId()); row.put("kind","candidate");
            row.put("name",candidates.findById(b.getCandidateId()).map(CandidateProfile::getName).orElse("Candidate"));
            row.put("holder",b.getAccountHolderName()); row.put("destination",maskDestination(b)); row.put("updatedAt",b.getUpdatedAt()); result.add(row);
        });
        return result;
    }
    @Transactional
    public void reviewAccount(CustomUserPrincipal actor, String kind, UUID id, BankReviewRequest request) {
        if ("hospital".equals(kind)) {
            var account = hospitalBanks.findByIdWithLock(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
            if (!Objects.equals(account.getUpdatedAt(), request.getExpectedUpdatedAt())) throw new BadRequestException("Bank details changed. Reload and review the current details.");
            account.setStatus(request.isApproved() ? "Verified" : "Under Review"); account.setVerifiedAt(request.isApproved() ? Instant.now() : null); hospitalBanks.save(account);
        } else if ("candidate".equals(kind)) {
            var account = candidateBanks.findByIdWithLock(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
            if (!Objects.equals(account.getUpdatedAt(), request.getExpectedUpdatedAt())) throw new BadRequestException("Payout details changed. Reload and review the current details.");
            account.setStatus(request.isApproved() ? "Verified" : "Pending"); account.setVerifiedAt(request.isApproved() ? Instant.now() : null); candidateBanks.save(account);
        } else throw new BadRequestException("Unknown account kind.");
        audit.logEvent(actor.getId(), request.isApproved() ? "BANK_OWNERSHIP_MANUALLY_VERIFIED" : "BANK_VERIFICATION_REJECTED", kind, id.toString(), Map.of("notes", request.getNotes().trim()));
    }
    public static String maskDestination(CandidatePayoutMethod method) {
        if ("upi".equalsIgnoreCase(method.getMethodType())) {
            String value = method.getUpiId();
            return value == null ? "UPI not recorded" : "UPI " + value.substring(0,Math.min(2,value.length())) + "***" + (value.contains("@") ? value.substring(value.indexOf('@')) : "");
        }
        return String.valueOf(method.getBankName()) + " ****" + method.getAccountNumberLast4();
    }
}
