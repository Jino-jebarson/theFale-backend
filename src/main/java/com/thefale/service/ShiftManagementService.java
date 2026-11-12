package com.thefale.service;

import com.thefale.dto.FacilityShiftPolicyDto;
import com.thefale.dto.LocumShiftDto;
import com.thefale.dto.PostShiftRequest;
import com.thefale.entity.*;
import com.thefale.exception.BadRequestException;
import com.thefale.exception.ForbiddenException;
import com.thefale.exception.ResourceNotFoundException;
import com.thefale.repository.*;
import com.thefale.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftManagementService {

    private static final Set<String> NON_FINAL_CLAIM_STATUSES = Set.of(
            "CANCELLED", "WITHDRAWN", "REJECTED", "SLOT_FILLED", "NO_SHOW"
    );
    private static final Set<String> LOCKED_DUTY_STATUSES = Set.of(
            "CONFIRMED", "CHECKED_IN", "IN_PROGRESS", "TIMESHEET_SUBMITTED", "COMPLETED", "DISPUTED"
    );

    private final FacilityProfileRepository facilityProfileRepository;
    private final FacilityService facilityService;
    private final FacilityWalletRepository facilityWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final LocumShiftRepository locumShiftRepository;
    private final ShiftClaimRepository shiftClaimRepository;
    private final MinimumRoleRateRepository minimumRoleRateRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final UserRepository userRepository;
    private final HospitalBankAccountRepository hospitalBankAccountRepository;
    private final PlatformSettingRepository platformSettingRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public FacilityShiftPolicyDto getFacilityShiftPolicy(CustomUserPrincipal principal) {
        FacilityProfile facility = facilityService.getOrCreateFacilityProfile(principal);
        boolean hasBank = hospitalBankAccountRepository.findByFacilityId(facility.getId()).isPresent();
        boolean kycVerified = "VERIFIED".equalsIgnoreCase(facility.getKycStatus());
        long priorApproved = locumShiftRepository.countApprovedShiftsByFacility(facility.getId());
        long priorValidFee = locumShiftRepository.countValidFeeShiftsByFacility(facility.getId());
        BigDecimal standardFee = getSettingAsDecimal("FACILITY_SHIFT_FEE_PERCENT", BigDecimal.valueOf(5.0));
        long availPaise = facilityWalletRepository.findByFacilityId(facility.getId())
                .map(w -> w.getAvailableBalance().multiply(BigDecimal.valueOf(100)).longValue())
                .orElse(0L);

        boolean firstShiftApprovalRequired = priorApproved == 0 || !kycVerified;
        boolean firstShiftFeeWaived = priorValidFee == 0;

        return FacilityShiftPolicyDto.builder()
                .hasLinkedBank(hasBank)
                .kycVerified(kycVerified)
                .firstShiftApprovalRequired(firstShiftApprovalRequired)
                .firstShiftFeeWaived(firstShiftFeeWaived)
                .platformFeePercent(firstShiftFeeWaived ? 0.0 : standardFee.doubleValue())
                .priorApprovedShiftsCount(priorApproved)
                .priorValidShiftsCount(priorValidFee)
                .availableBalancePaise(availPaise)
                .build();
    }

    @Transactional
    public LocumShiftDto postShift(CustomUserPrincipal principal, PostShiftRequest request) {
        FacilityProfile facility = facilityService.getOrCreateFacilityProfile(principal);

        if (hospitalBankAccountRepository.findByFacilityId(facility.getId()).isEmpty()) {
            throw new BadRequestException("Hospital settlement bank account must be linked and verified before broadcasting locum shifts (required for escrow custody and refunds).");
        }

        validateShiftTimesAndFloor(request);
        int hours = resolveValidatedHours(request);

        BigDecimal baseRateRupees = BigDecimal.valueOf(request.getRatePerHourPaise())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal surgeRateRupees = BigDecimal.valueOf(request.getSurgeRatePerHourPaise() != null ? request.getSurgeRatePerHourPaise() : 0L)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // Facility fee: 0% on facility's 1st valid (non-cancelled/rejected) shift, 5% from 2nd+ shift onward
        long priorValidFeeShifts = locumShiftRepository.countValidFeeShiftsByFacility(facility.getId());
        BigDecimal standardFacilityFeePct = getSettingAsDecimal("FACILITY_SHIFT_FEE_PERCENT", BigDecimal.valueOf(5.0));
        BigDecimal facilityFeePercent = priorValidFeeShifts == 0 ? BigDecimal.ZERO : standardFacilityFeePct;

        BigDecimal totalPerWorker = baseRateRupees.add(surgeRateRupees).multiply(BigDecimal.valueOf(hours));
        BigDecimal workerEscrowTotal = totalPerWorker.multiply(BigDecimal.valueOf(request.getOpenings()));
        BigDecimal facilityFeeAmount = workerEscrowTotal.multiply(facilityFeePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal totalEscrowRequired = workerEscrowTotal.add(facilityFeeAmount);

        FacilityWallet wallet = facilityWalletRepository.findByFacilityIdWithLock(facility.getId())
                .orElseThrow(() -> new BadRequestException("Facility wallet not initialized. Please onboard and top up your escrow reserve."));

        if (wallet.getAvailableBalance().compareTo(totalEscrowRequired) < 0) {
            throw new BadRequestException("Insufficient escrow reserve. Required: ₹" + totalEscrowRequired +
                    " (Shift Escrow ₹" + workerEscrowTotal + " + Platform Fee ₹" + facilityFeeAmount +
                    "), Available: ₹" + wallet.getAvailableBalance() + ". Please top up your wallet escrow reserve before broadcasting shifts.");
        }

        wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(totalEscrowRequired));
        wallet.setLockedEscrowBalance(wallet.getLockedEscrowBalance().add(totalEscrowRequired));
        facilityWalletRepository.save(wallet);

        String feeSummary = facilityFeePercent.compareTo(BigDecimal.ZERO) == 0
                ? "0% First-Shift Fee Waiver"
                : facilityFeePercent.stripTrailingZeros().toPlainString() + "% Fee: ₹" + facilityFeeAmount;
        String escrowRef = "ESCROW-HOLD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        WalletTransaction tx = WalletTransaction.builder()
                .walletId(wallet.getId())
                .type("escrow_hold")
                .amount(totalEscrowRequired.negate())
                .balanceAfter(wallet.getAvailableBalance())
                .status("In Escrow")
                .refNumber(escrowRef)
                .title("Escrow Lock: " + request.getTitle())
                .subtitle(request.getOpenings() + " slot(s) · " + hours + " hrs @ ₹" + (baseRateRupees.add(surgeRateRupees)) + "/hr (" + feeSummary + ")")
                .build();
        walletTransactionRepository.save(tx);

        if (wallet.getAvailableBalance().compareTo(new BigDecimal("10000")) < 0) {
            notificationRepository.save(Notification.builder()
                    .userId(facility.getUserId())
                    .title("Low Escrow Reserve Alert")
                    .message("Your available wallet balance is ₹" + wallet.getAvailableBalance() + ". Top up your escrow reserve to ensure future locum shifts can be broadcast.")
                    .type("WALLET")
                    .actionUrl("/employer/wallet")
                    .read(false)
                    .build());
        }

        // First-shift Admin gate: 1st shift (or unverified KYC facility) needs Admin approval;
        // 2nd+ shift goes OPEN immediately once bank, wage floor, and escrow checks pass.
        boolean kycVerified = "VERIFIED".equalsIgnoreCase(facility.getKycStatus());
        long priorApprovedCount = locumShiftRepository.countApprovedShiftsByFacility(facility.getId());
        boolean autoApprove = kycVerified && priorApprovedCount > 0;
        String initialStatus = autoApprove ? "OPEN" : "PENDING_APPROVAL";

        LocumShift shift = LocumShift.builder()
                .facilityId(facility.getId())
                .title(request.getTitle().trim())
                .department(request.getDepartment().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .hours(hours)
                .ratePerHour(baseRateRupees)
                .surgeRatePerHour(surgeRateRupees)
                .surgeLabel(request.getSurgeLabel())
                .openings(request.getOpenings())
                .slotsClaimed(0)
                .urgent(request.isUrgent())
                .status(initialStatus)
                .facilityFeePercent(facilityFeePercent)
                .facilityFeeAmount(facilityFeeAmount)
                .autoApproved(autoApprove)
                .build();

        LocumShift saved = locumShiftRepository.save(shift);
        auditLogService.logEvent(principal.getId(), autoApprove ? "SHIFT_POSTED_AUTO_LIVE" : "SHIFT_POSTED", "LocumShift", saved.getId().toString(), null);

        if (autoApprove) {
            notifyUrgentLocalClinicians(saved, facility);
        }

        return mapToDto(saved, facility, false);
    }

    @Transactional
    public LocumShiftDto updateShift(CustomUserPrincipal principal, UUID shiftId, PostShiftRequest request) {
        FacilityProfile facility = facilityService.getOrCreateFacilityProfile(principal);

        LocumShift shift = locumShiftRepository.findByIdWithLock(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Locum shift not found"));

        if (!shift.getFacilityId().equals(facility.getId())) {
            throw new ForbiddenException("Unauthorized to update shift for this facility");
        }

        if (!Set.of("PENDING_APPROVAL", "OPEN", "FILLED").contains(shift.getStatus().toUpperCase())) {
            throw new BadRequestException("Cannot update shift in status " + shift.getStatus());
        }

        validateShiftTimesAndFloor(request);
        int newHours = resolveValidatedHours(request);

        BigDecimal newBaseRate = BigDecimal.valueOf(request.getRatePerHourPaise())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal newSurgeRate = BigDecimal.valueOf(request.getSurgeRatePerHourPaise() != null ? request.getSurgeRatePerHourPaise() : 0L)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        List<ShiftClaim> activeClaims = shiftClaimRepository.findByShiftId(shift.getId()).stream()
                .filter(c -> c.getStatus() != null && !NON_FINAL_CLAIM_STATUSES.contains(c.getStatus().toUpperCase()))
                .collect(Collectors.toList());

        if (!activeClaims.isEmpty()) {
            BigDecimal oldEffective = shift.getRatePerHour().add(shift.getSurgeRatePerHour());
            BigDecimal newEffective = newBaseRate.add(newSurgeRate);
            if (newEffective.compareTo(oldEffective) < 0) {
                throw new BadRequestException("Cannot reduce hourly rate after clinicians have already claimed slots.");
            }
            if (!shift.getStartAt().equals(request.getStartAt()) || !shift.getEndAt().equals(request.getEndAt()) || shift.getHours() != newHours) {
                throw new BadRequestException("Cannot alter shift dates or duration after clinicians have already claimed slots.");
            }
            if (request.getOpenings() < activeClaims.size()) {
                throw new BadRequestException("Openings (" + request.getOpenings() + ") cannot be fewer than currently active claims (" + activeClaims.size() + ").");
            }
        }

        // Compute old vs new locked escrow (preserving stored facilityFeePercent on this shift)
        BigDecimal oldWorkerEscrow = shift.getRatePerHour().add(shift.getSurgeRatePerHour())
                .multiply(BigDecimal.valueOf(shift.getHours()))
                .multiply(BigDecimal.valueOf(shift.getOpenings()));
        BigDecimal oldTotalEscrow = oldWorkerEscrow.add(shift.getFacilityFeeAmount() != null ? shift.getFacilityFeeAmount() : BigDecimal.ZERO);

        BigDecimal feePct = shift.getFacilityFeePercent() != null ? shift.getFacilityFeePercent() : BigDecimal.ZERO;
        BigDecimal newWorkerEscrow = newBaseRate.add(newSurgeRate)
                .multiply(BigDecimal.valueOf(newHours))
                .multiply(BigDecimal.valueOf(request.getOpenings()));
        BigDecimal newFacilityFee = newWorkerEscrow.multiply(feePct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal newTotalEscrow = newWorkerEscrow.add(newFacilityFee);

        BigDecimal delta = newTotalEscrow.subtract(oldTotalEscrow);
        if (delta.compareTo(BigDecimal.ZERO) != 0) {
            FacilityWallet wallet = facilityWalletRepository.findByFacilityIdWithLock(facility.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Facility wallet not found"));
            if (delta.compareTo(BigDecimal.ZERO) > 0) {
                if (wallet.getAvailableBalance().compareTo(delta) < 0) {
                    throw new BadRequestException("Insufficient escrow reserve for updated shift. Additional escrow required: ₹" + delta + ", Available: ₹" + wallet.getAvailableBalance());
                }
                wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(delta));
                wallet.setLockedEscrowBalance(wallet.getLockedEscrowBalance().add(delta));
                facilityWalletRepository.save(wallet);

                walletTransactionRepository.save(WalletTransaction.builder()
                        .walletId(wallet.getId())
                        .type("escrow_hold")
                        .amount(delta.negate())
                        .balanceAfter(wallet.getAvailableBalance())
                        .status("In Escrow")
                        .refNumber("ESCROW-UPD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .title("Shift Update Escrow Top-Up: " + request.getTitle().trim())
                        .subtitle("Additional escrow locked on shift edit")
                        .build());
            } else {
                BigDecimal refund = delta.abs();
                BigDecimal safeSub = refund.min(wallet.getLockedEscrowBalance());
                wallet.setLockedEscrowBalance(wallet.getLockedEscrowBalance().subtract(safeSub));
                wallet.setAvailableBalance(wallet.getAvailableBalance().add(refund));
                facilityWalletRepository.save(wallet);

                walletTransactionRepository.save(WalletTransaction.builder()
                        .walletId(wallet.getId())
                        .type("refund")
                        .amount(refund)
                        .balanceAfter(wallet.getAvailableBalance())
                        .status("Refunded")
                        .refNumber("REFUND-UPD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .title("Shift Update Escrow Refund: " + request.getTitle().trim())
                        .subtitle("Excess escrow released after shift update")
                        .build());
            }
        }

        shift.setTitle(request.getTitle().trim());
        shift.setDepartment(request.getDepartment().trim());
        shift.setDescription(request.getDescription() != null ? request.getDescription().trim() : shift.getDescription());
        shift.setStartAt(request.getStartAt());
        shift.setEndAt(request.getEndAt());
        shift.setHours(newHours);
        shift.setRatePerHour(newBaseRate);
        shift.setSurgeRatePerHour(newSurgeRate);
        shift.setSurgeLabel(request.getSurgeLabel());
        shift.setOpenings(request.getOpenings());
        shift.setUrgent(request.isUrgent());
        shift.setFacilityFeeAmount(newFacilityFee);
        shift.setSlotsClaimed(activeClaims.size());

        if (!"PENDING_APPROVAL".equalsIgnoreCase(shift.getStatus())) {
            shift.setStatus(activeClaims.size() >= request.getOpenings() ? "FILLED" : "OPEN");
        }

        LocumShift saved = locumShiftRepository.save(shift);
        auditLogService.logEvent(principal.getId(), "SHIFT_UPDATED", "LocumShift", saved.getId().toString(), null);
        return mapToDto(saved, facility, false);
    }

    @Transactional(readOnly = true)
    public List<LocumShiftDto> getOpenShifts(CustomUserPrincipal principal) {
        CandidateProfile candidate = null;
        boolean candIsStudent = false;
        UUID candidateId = null;
        if (principal != null && "ROLE_CANDIDATE".equalsIgnoreCase(principal.getRole())) {
            candidate = candidateProfileRepository.findByUserId(principal.getId()).orElse(null);
            candidateId = candidate != null ? candidate.getId() : null;
            candIsStudent = userRepository.findById(principal.getId())
                    .map(u -> "STUDENT".equalsIgnoreCase(u.getCandidateLevel()))
                    .orElse(false);
        }

        List<LocumShift> openShifts = locumShiftRepository.findOpenShifts();
        java.util.Map<UUID, LocumShift> orderedShifts = new java.util.LinkedHashMap<>();
        openShifts.forEach(s -> orderedShifts.put(s.getId(), s));

        java.util.Map<UUID, ShiftClaim> claimsByShiftId = new java.util.HashMap<>();
        if (candidateId != null) {
            List<ShiftClaim> myClaims = shiftClaimRepository.findByCandidateIdOrderByClaimedAtDesc(candidateId);
            java.util.List<UUID> missingShiftIds = new java.util.ArrayList<>();
            for (ShiftClaim c : myClaims) {
                claimsByShiftId.putIfAbsent(c.getShiftId(), c);
                String st = c.getStatus() != null ? c.getStatus().toUpperCase() : "";
                boolean isWithdrawnOrCancelled = "CANCELLED".equals(st) || "WITHDRAWN".equals(st);
                if (!isWithdrawnOrCancelled && !orderedShifts.containsKey(c.getShiftId())) {
                    missingShiftIds.add(c.getShiftId());
                }
            }
            if (!missingShiftIds.isEmpty()) {
                java.util.Map<UUID, LocumShift> loaded = locumShiftRepository.findAllById(missingShiftIds).stream()
                        .filter(s -> s.getStatus() != null && !"CANCELLED".equalsIgnoreCase(s.getStatus()))
                        .collect(Collectors.toMap(LocumShift::getId, s -> s));
                for (UUID sid : missingShiftIds) {
                    LocumShift claimedShift = loaded.get(sid);
                    if (claimedShift != null) {
                        orderedShifts.putIfAbsent(sid, claimedShift);
                    }
                }
            }
        }

        final CandidateProfile finalCandidate = candidate;
        final boolean finalCandIsStudent = candIsStudent;
        return orderedShifts.values().stream()
                .map(s -> {
                    FacilityProfile facility = facilityProfileRepository.findById(s.getFacilityId()).orElse(null);
                    ShiftClaim claim = claimsByShiftId.get(s.getId());
                    boolean hasClaimed = claim != null && claim.getStatus() != null
                            && !NON_FINAL_CLAIM_STATUSES.contains(claim.getStatus().toUpperCase());
                    Integer matchScore = finalCandidate != null
                            ? ClinicalMatchingEngine.calculateShiftMatch(finalCandidate, finalCandIsStudent, s, facility)
                            : null;
                    return mapToDto(s, facility, hasClaimed, matchScore);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LocumShiftDto> getFacilityShifts(CustomUserPrincipal principal) {
        FacilityProfile facility = facilityService.getOrCreateFacilityProfile(principal);

        return locumShiftRepository.findByFacilityIdOrderByStartAtDesc(facility.getId()).stream()
                .map(s -> mapToDto(s, facility, false))
                .collect(Collectors.toList());
    }

    @Transactional
    public void cancelShift(CustomUserPrincipal principal, UUID shiftId) {
        FacilityProfile facility = facilityService.getOrCreateFacilityProfile(principal);

        LocumShift shift = locumShiftRepository.findByIdWithLock(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Locum shift not found"));

        if (!shift.getFacilityId().equals(facility.getId())) {
            throw new ForbiddenException("Unauthorized to cancel shift for this facility");
        }

        // Strict double-refund guard
        String currentStatus = shift.getStatus() != null ? shift.getStatus().toUpperCase() : "";
        if (!Set.of("PENDING_APPROVAL", "OPEN", "FILLED").contains(currentStatus)) {
            throw new BadRequestException("Shift is already " + shift.getStatus() + " and cannot be cancelled again.");
        }

        List<ShiftClaim> shiftClaims = shiftClaimRepository.findByShiftId(shift.getId());
        boolean hasLockedDuty = shiftClaims.stream()
                .anyMatch(c -> c.getStatus() != null && LOCKED_DUTY_STATUSES.contains(c.getStatus().toUpperCase()));
        if (hasLockedDuty) {
            throw new BadRequestException("Cannot cancel shift with confirmed, checked-in, or completed clinician duties. Resolve active claims first.");
        }

        // Cleanly cancel any pending/unconfirmed CLAIMS and notify candidates
        for (ShiftClaim c : shiftClaims) {
            if (c.getStatus() != null && Set.of("CLAIMED", "CLAIM_PENDING_ADMIN").contains(c.getStatus().toUpperCase())) {
                c.setStatus("CANCELLED");
                c.setCancelledAt(Instant.now());
                c.setPayoutStatus("REFUNDED");
                shiftClaimRepository.save(c);

                candidateProfileRepository.findById(c.getCandidateId()).ifPresent(cand ->
                        notificationRepository.save(Notification.builder()
                                .userId(cand.getUserId())
                                .title("Shift Cancelled by Facility: " + shift.getTitle())
                                .message(facility.getHospitalName() + " cancelled the shift \"" + shift.getTitle() + "\". Your schedule is now open for other shifts.")
                                .type("SHIFT")
                                .actionUrl("/shifts")
                                .read(false)
                                .build())
                );
            }
        }

        shift.setStatus("CANCELLED");
        shift.setSlotsClaimed(0);
        locumShiftRepository.save(shift);

        BigDecimal workerEscrow = shift.getRatePerHour().add(shift.getSurgeRatePerHour())
                .multiply(BigDecimal.valueOf(shift.getHours()))
                .multiply(BigDecimal.valueOf(shift.getOpenings()));
        BigDecimal refundAmount = workerEscrow.add(shift.getFacilityFeeAmount() != null ? shift.getFacilityFeeAmount() : BigDecimal.ZERO);

        FacilityWallet wallet = facilityWalletRepository.findByFacilityIdWithLock(facility.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Facility wallet not found"));

        BigDecimal safeLockedSub = refundAmount.min(wallet.getLockedEscrowBalance());
        wallet.setLockedEscrowBalance(wallet.getLockedEscrowBalance().subtract(safeLockedSub));
        wallet.setAvailableBalance(wallet.getAvailableBalance().add(refundAmount));
        facilityWalletRepository.save(wallet);

        String refundRef = "REFUND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        WalletTransaction tx = WalletTransaction.builder()
                .walletId(wallet.getId())
                .type("refund")
                .amount(refundAmount)
                .balanceAfter(wallet.getAvailableBalance())
                .status("Refunded")
                .refNumber(refundRef)
                .title("Escrow Refund: " + shift.getTitle())
                .subtitle("Cancelled shift full escrow + fee refund")
                .build();
        walletTransactionRepository.save(tx);

        auditLogService.logEvent(principal.getId(), "SHIFT_CANCELLED", "LocumShift", shift.getId().toString(), null);
    }

    @Scheduled(fixedDelay = 900_000)
    @Transactional
    public void expirePastUnfilledShifts() {
        List<LocumShift> expiredCandidates = locumShiftRepository.findExpiredUnfilledShifts(Instant.now());
        for (LocumShift candidateShift : expiredCandidates) {
            try {
                LocumShift shift = locumShiftRepository.findByIdWithLock(candidateShift.getId()).orElse(null);
                if (shift == null || !Set.of("PENDING_APPROVAL", "OPEN").contains(shift.getStatus().toUpperCase())) {
                    continue;
                }
                List<ShiftClaim> claims = shiftClaimRepository.findByShiftId(shift.getId());
                boolean hasLockedDuty = claims.stream()
                        .anyMatch(c -> c.getStatus() != null && LOCKED_DUTY_STATUSES.contains(c.getStatus().toUpperCase()));
                if (hasLockedDuty) {
                    continue;
                }

                for (ShiftClaim c : claims) {
                    if (c.getStatus() != null && Set.of("CLAIMED", "CLAIM_PENDING_ADMIN").contains(c.getStatus().toUpperCase())) {
                        c.setStatus("CANCELLED");
                        c.setCancelledAt(Instant.now());
                        c.setPayoutStatus("REFUNDED");
                        shiftClaimRepository.save(c);
                    }
                }

                shift.setStatus("EXPIRED");
                shift.setSlotsClaimed(0);
                locumShiftRepository.save(shift);

                BigDecimal workerEscrow = shift.getRatePerHour().add(shift.getSurgeRatePerHour())
                        .multiply(BigDecimal.valueOf(shift.getHours()))
                        .multiply(BigDecimal.valueOf(shift.getOpenings()));
                BigDecimal refundAmount = workerEscrow.add(shift.getFacilityFeeAmount() != null ? shift.getFacilityFeeAmount() : BigDecimal.ZERO);

                facilityWalletRepository.findByFacilityIdWithLock(shift.getFacilityId()).ifPresent(wallet -> {
                    BigDecimal safeSub = refundAmount.min(wallet.getLockedEscrowBalance());
                    wallet.setLockedEscrowBalance(wallet.getLockedEscrowBalance().subtract(safeSub));
                    wallet.setAvailableBalance(wallet.getAvailableBalance().add(refundAmount));
                    facilityWalletRepository.save(wallet);

                    walletTransactionRepository.save(WalletTransaction.builder()
                            .walletId(wallet.getId())
                            .type("refund")
                            .amount(refundAmount)
                            .balanceAfter(wallet.getAvailableBalance())
                            .status("Refunded")
                            .refNumber("EXP-REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                            .title("Expired Shift Auto-Refund: " + shift.getTitle())
                            .subtitle("Shift window passed without confirmed claims; escrow returned to wallet")
                            .build());
                });

                facilityProfileRepository.findById(shift.getFacilityId()).ifPresent(fac ->
                        notificationRepository.save(Notification.builder()
                                .userId(fac.getUserId())
                                .title("Shift Expired & Escrow Refunded")
                                .message("Your shift \"" + shift.getTitle() + "\" ended unfilled and was closed automatically. ₹" + refundAmount + " was refunded to your wallet.")
                                .type("SHIFT")
                                .actionUrl("/employer/wallet")
                                .read(false)
                                .build())
                );
            } catch (Exception e) {
                log.warn("Failed to auto-expire shift {}: {}", candidateShift.getId(), e.getMessage());
            }
        }
    }

    public void notifyUrgentLocalClinicians(LocumShift shift, FacilityProfile facility) {
        if (facility == null) return;
        boolean urgentOrSurge = shift.isUrgent() || (shift.getSurgeRatePerHour() != null && shift.getSurgeRatePerHour().compareTo(BigDecimal.ZERO) > 0);
        if (!urgentOrSurge) return;

        String surgeInfo = shift.getSurgeLabel() != null && !shift.getSurgeLabel().isBlank()
                ? " (" + shift.getSurgeLabel() + " +₹" + shift.getSurgeRatePerHour() + "/hr surge)"
                : "";

        List<CandidateProfile> localCandidates = candidateProfileRepository.findAll();
        for (CandidateProfile cand : localCandidates) {
            if (!"VERIFIED".equalsIgnoreCase(cand.getKycStatus())) continue;
            if (cand.getCity() == null || facility.getCity() == null || !cand.getCity().equalsIgnoreCase(facility.getCity())) continue;
            User user = cand.getUserId() != null ? userRepository.findById(cand.getUserId()).orElse(null) : null;
            if (user != null && "STUDENT".equalsIgnoreCase(user.getCandidateLevel())) continue;

            // Give Top-Rated verified clinicians early highlight
            boolean topRated = cand.getShiftRatingCount() != null && cand.getShiftRatingCount() >= 3
                    && cand.getShiftRatingAvg() != null && cand.getShiftRatingAvg().compareTo(new BigDecimal("4.50")) >= 0;

            notificationRepository.save(Notification.builder()
                    .userId(cand.getUserId())
                    .title((topRated ? "[Top-Rated Priority] " : "") + "Urgent On-Call Shift: " + shift.getTitle())
                    .message(facility.getHospitalName() + " posted an emergency duty: " + shift.getTitle() + surgeInfo + " in " + facility.getCity() + ". Guaranteed escrow payout.")
                    .type("SHIFT_URGENT")
                    .actionUrl("/shifts")
                    .read(false)
                    .build());
        }
    }

    private void validateShiftTimesAndFloor(PostShiftRequest request) {
        if (request.getStartAt().isBefore(Instant.now().minusSeconds(300))) {
            throw new BadRequestException("Shift start time must be in the future.");
        }
        if (!request.getEndAt().isAfter(request.getStartAt())) {
            throw new BadRequestException("Shift end time must be after start time");
        }
        int hours = resolveValidatedHours(request);
        if (hours < 4 || hours > 24) {
            throw new BadRequestException("Shift duration must be between 4 and 24 hours. Given duration: " + hours + " hours");
        }

        BigDecimal baseRateRupees = BigDecimal.valueOf(request.getRatePerHourPaise())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        minimumRoleRateRepository.findAll().stream()
                .filter(floor -> request.getTitle().toLowerCase().contains(floor.getProfession().toLowerCase()) ||
                        floor.getProfession().toLowerCase().contains(request.getTitle().toLowerCase()))
                .findFirst()
                .ifPresent(floor -> {
                    if (baseRateRupees.compareTo(floor.getMinRatePerHour()) < 0) {
                        throw new BadRequestException("Statutory wage floor violation: " + floor.getProfession() +
                                " minimum rate is ₹" + floor.getMinRatePerHour() + "/hr. Given rate: ₹" + baseRateRupees + "/hr.");
                    }
                });
    }

    private int resolveValidatedHours(PostShiftRequest request) {
        long minutes = Duration.between(request.getStartAt(), request.getEndAt()).toMinutes();
        long roundedHours = Math.round(minutes / 60.0);
        if (roundedHours <= 0 && request.getHours() != null && request.getHours() > 0) {
            roundedHours = request.getHours();
        }
        return (int) roundedHours;
    }

    private BigDecimal getSettingAsDecimal(String key, BigDecimal defaultValue) {
        return platformSettingRepository.findById(key)
                .map(s -> {
                    try {
                        return new BigDecimal(s.getValue());
                    } catch (Exception e) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    public LocumShiftDto mapToDto(LocumShift s, FacilityProfile facility, boolean hasClaimed) {
        return mapToDto(s, facility, hasClaimed, null);
    }

    public LocumShiftDto mapToDto(LocumShift s, FacilityProfile facility, boolean hasClaimed, Integer matchScore) {
        long ratePaise = s.getRatePerHour().multiply(BigDecimal.valueOf(100)).longValue();
        long surgePaise = s.getSurgeRatePerHour().multiply(BigDecimal.valueOf(100)).longValue();
        long totalPayPaise = (ratePaise + surgePaise) * s.getHours();
        long facilityFeePaise = s.getFacilityFeeAmount() != null
                ? s.getFacilityFeeAmount().multiply(BigDecimal.valueOf(100)).longValue()
                : 0L;
        long totalEscrowPaise = totalPayPaise * s.getOpenings() + facilityFeePaise;

        return LocumShiftDto.builder()
                .id(s.getId())
                .facilityId(s.getFacilityId())
                .facilityName(facility != null ? facility.getHospitalName() : "Hospital")
                .city(facility != null ? facility.getCity() : "City")
                .title(s.getTitle())
                .department(s.getDepartment())
                .description(s.getDescription())
                .startAt(s.getStartAt())
                .endAt(s.getEndAt())
                .hours(s.getHours())
                .ratePerHourPaise(ratePaise)
                .surgeRatePerHourPaise(surgePaise)
                .surgeLabel(s.getSurgeLabel())
                .totalPayPaise(totalPayPaise)
                .openings(s.getOpenings())
                .slotsClaimed(s.getSlotsClaimed())
                .urgent(s.isUrgent())
                .status(s.getStatus())
                .facilityFeePercent(s.getFacilityFeePercent() != null ? s.getFacilityFeePercent().doubleValue() : 0.0)
                .facilityFeePaise(facilityFeePaise)
                .totalEscrowRequiredPaise(totalEscrowPaise)
                .autoApproved(s.isAutoApproved())
                .hasClaimed(hasClaimed)
                .matchScore(matchScore)
                .build();
    }
}
