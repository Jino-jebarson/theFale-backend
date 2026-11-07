package com.thefale.service;

import com.thefale.dto.BuyCreditsRequest;
import com.thefale.dto.FacilityWalletDto;
import com.thefale.dto.WalletTopUpRequest;
import com.thefale.dto.WalletTransactionDto;
import com.thefale.entity.FacilityProfile;
import com.thefale.entity.FacilityWallet;
import com.thefale.entity.WalletTransaction;
import com.thefale.exception.BadRequestException;
import com.thefale.exception.ConflictException;
import com.thefale.exception.ResourceNotFoundException;
import com.thefale.repository.FacilityProfileRepository;
import com.thefale.repository.FacilityWalletRepository;
import com.thefale.repository.WalletTransactionRepository;
import com.thefale.repository.HospitalBankAccountRepository;
import com.thefale.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityWalletService {

    private final FacilityProfileRepository facilityProfileRepository;
    private final FacilityService facilityService;
    private final FacilityWalletRepository facilityWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final HospitalBankAccountRepository hospitalBankAccountRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public FacilityWalletDto getOrCreateWallet(CustomUserPrincipal principal) {
        FacilityProfile facility = facilityService.getOrCreateFacilityProfile(principal);

        FacilityWallet wallet = facilityWalletRepository.findByFacilityId(facility.getId())
                .orElseGet(() -> facilityWalletRepository.save(FacilityWallet.builder()
                        .facilityId(facility.getId())
                        .availableBalance(BigDecimal.ZERO)
                        .lockedEscrowBalance(BigDecimal.ZERO)
                        .totalDisbursed(BigDecimal.ZERO)
                        .candidateCredits(0)
                        .build()));

        // Self-heal legacy wallet rows that were created with the old static default of 3
        if (wallet.getCandidateCredits() == 3) {
            wallet.setCandidateCredits(0);
            wallet = facilityWalletRepository.save(wallet);
        }

        return mapWalletToDto(wallet);
    }

    @Transactional
    public List<WalletTransactionDto> getTransactions(CustomUserPrincipal principal) {
        FacilityProfile facility = facilityService.getOrCreateFacilityProfile(principal);

        FacilityWallet wallet = facilityWalletRepository.findByFacilityId(facility.getId())
                .orElseGet(() -> facilityWalletRepository.save(FacilityWallet.builder()
                        .facilityId(facility.getId())
                        .availableBalance(BigDecimal.ZERO)
                        .lockedEscrowBalance(BigDecimal.ZERO)
                        .totalDisbursed(BigDecimal.ZERO)
                        .candidateCredits(0)
                        .build()));

        return walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId()).stream()
                .map(this::mapTxToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public FacilityWalletDto topUp(CustomUserPrincipal principal, WalletTopUpRequest request) {
        FacilityProfile facility = facilityService.getOrCreateFacilityProfile(principal);

        if (hospitalBankAccountRepository.findByFacilityId(facility.getId()).isEmpty()) {
            throw new BadRequestException("Hospital settlement bank account must be linked and verified before topping up escrow.");
        }

        String utr = request.getUtr().trim().toUpperCase();
        if (walletTransactionRepository.existsByUtr(utr)) {
            throw new ConflictException("UTR reference " + utr + " has already been submitted or processed");
        }

        FacilityWallet wallet = facilityWalletRepository.findByFacilityIdWithLock(facility.getId())
                .orElseGet(() -> facilityWalletRepository.save(FacilityWallet.builder()
                        .facilityId(facility.getId())
                        .availableBalance(BigDecimal.ZERO)
                        .lockedEscrowBalance(BigDecimal.ZERO)
                        .totalDisbursed(BigDecimal.ZERO)
                        .candidateCredits(0)
                        .build()));

        // Convert paise to Rupees (e.g. 2500000 paise -> 25000.00)
        BigDecimal depositAmount = BigDecimal.valueOf(request.getAmountPaise())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal newBalance = wallet.getAvailableBalance().add(depositAmount);
        wallet.setAvailableBalance(newBalance);
        facilityWalletRepository.save(wallet);

        String refNumber = "TXN-TOPUP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String invoiceId = "INV-2026-" + (1000 + (int)(Math.random() * 9000));

        WalletTransaction tx = WalletTransaction.builder()
                .walletId(wallet.getId())
                .type("topup")
                .amount(depositAmount)
                .balanceAfter(newBalance)
                .status("Completed")
                .refNumber(refNumber)
                .utr(utr)
                .title("Escrow Deposit (" + request.getPaymentMethod().toUpperCase() + ")")
                .subtitle("Verified banking top-up via UTR " + utr)
                .invoiceId(invoiceId)
                .build();

        walletTransactionRepository.save(tx);

        auditLogService.logEvent(principal.getId(), "WALLET_TOPPED_UP", "FacilityWallet", wallet.getId().toString(), null);

        return mapWalletToDto(wallet);
    }

    @Transactional
    public FacilityWalletDto buyCredits(CustomUserPrincipal principal, BuyCreditsRequest request) {
        FacilityProfile facility = facilityService.getOrCreateFacilityProfile(principal);

        if (hospitalBankAccountRepository.findByFacilityId(facility.getId()).isEmpty()) {
            throw new BadRequestException("Hospital settlement bank account must be linked and verified before purchasing sourcing credits.");
        }

        FacilityWallet wallet = facilityWalletRepository.findByFacilityIdWithLock(facility.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Facility wallet not found"));

        int creditsToAdd;
        BigDecimal costRupees;
        String packageName;

        switch (request.getPackageId().trim().toLowerCase()) {
            case "pkg-1":
            case "pkg_10":
                creditsToAdd = 10;
                costRupees = BigDecimal.valueOf(1999.00);
                packageName = "Starter (10 credits)";
                break;
            case "pkg-2":
            case "pkg_30":
                creditsToAdd = 30;
                costRupees = BigDecimal.valueOf(4999.00);
                packageName = "Growth (30 credits)";
                break;
            case "pkg-3":
            case "pkg_100":
                creditsToAdd = 100;
                costRupees = BigDecimal.valueOf(12999.00);
                packageName = "Enterprise (100 credits)";
                break;
            default:
                throw new BadRequestException("Invalid credit package ID: " + request.getPackageId());
        }

        if (request.getCredits() != null && request.getCredits() > 0) {
            creditsToAdd = request.getCredits();
        }

        BigDecimal newBalance = wallet.getAvailableBalance();
        boolean paidFromEscrow = false;
        if (wallet.getAvailableBalance() != null && wallet.getAvailableBalance().compareTo(costRupees) >= 0) {
            newBalance = wallet.getAvailableBalance().subtract(costRupees);
            wallet.setAvailableBalance(newBalance);
            paidFromEscrow = true;
        }

        wallet.setCandidateCredits(wallet.getCandidateCredits() + creditsToAdd);
        facilityWalletRepository.save(wallet);

        String refNumber = "CRD-FALE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String invoiceId = "INV-2026-" + (1000 + (int)(Math.random() * 9000));

        WalletTransaction tx = WalletTransaction.builder()
                .walletId(wallet.getId())
                .type("credit_purchase")
                .amount(paidFromEscrow ? costRupees.negate() : costRupees)
                .balanceAfter(newBalance)
                .status("Completed")
                .refNumber(refNumber)
                .title("Sourcing Credits (" + packageName + ")")
                .subtitle(paidFromEscrow
                        ? creditsToAdd + " verified candidate contact unlocks (Deducted from Escrow)"
                        : creditsToAdd + " verified candidate contact unlocks (Online Payment)")
                .invoiceId(invoiceId)
                .build();

        walletTransactionRepository.save(tx);

        auditLogService.logEvent(principal.getId(), "CREDITS_PURCHASED", "FacilityWallet", wallet.getId().toString(), null);

        return mapWalletToDto(wallet);
    }

    private FacilityWalletDto mapWalletToDto(FacilityWallet w) {
        return FacilityWalletDto.builder()
                .id(w.getId())
                .facilityId(w.getFacilityId())
                .availableBalancePaise(w.getAvailableBalance().multiply(BigDecimal.valueOf(100)).longValue())
                .lockedEscrowBalancePaise(w.getLockedEscrowBalance().multiply(BigDecimal.valueOf(100)).longValue())
                .totalDisbursedPaise(w.getTotalDisbursed().multiply(BigDecimal.valueOf(100)).longValue())
                .candidateCredits(w.getCandidateCredits())
                .updatedAt(w.getUpdatedAt())
                .build();
    }

    private WalletTransactionDto mapTxToDto(WalletTransaction t) {
        return WalletTransactionDto.builder()
                .id(t.getId())
                .walletId(t.getWalletId())
                .type(t.getType())
                .amountPaise(t.getAmount().multiply(BigDecimal.valueOf(100)).longValue())
                .balanceAfterPaise(t.getBalanceAfter().multiply(BigDecimal.valueOf(100)).longValue())
                .status(t.getStatus())
                .refNumber(t.getRefNumber())
                .utr(t.getUtr())
                .title(t.getTitle())
                .subtitle(t.getSubtitle())
                .invoiceId(t.getInvoiceId())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
