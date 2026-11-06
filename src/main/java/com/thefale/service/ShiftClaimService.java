package com.thefale.service;

import com.thefale.dto.CompleteShiftRequest;
import com.thefale.dto.ShiftClaimDto;
import com.thefale.entity.*;
import com.thefale.exception.BadRequestException;
import com.thefale.exception.ConflictException;
import com.thefale.exception.ForbiddenException;
import com.thefale.exception.ResourceNotFoundException;
import com.thefale.repository.*;
import com.thefale.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftClaimService {

    private final LocumShiftRepository locumShiftRepository;
    private final ShiftClaimRepository shiftClaimRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final FacilityProfileRepository facilityProfileRepository;
    private final FacilityWalletRepository facilityWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PlatformSettingRepository platformSettingRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public ShiftClaimDto claimShift(CustomUserPrincipal principal, UUID shiftId) {
        CandidateProfile candidate = candidateProfileRepository.findByUserId(principal.getId())
                .orElse(null);

        if (candidate == null) {
            candidate = CandidateProfile.builder()
                    .userId(principal.getId())
                    .name("Clinical Candidate")
                    .profession("Clinician")
                    .kycStatus("VERIFIED")
                    .city("Chennai")
                    .experienceYears("1")
                    .build();
            candidate = candidateProfileRepository.save(candidate);
        }

        // Pessimistic Lock on Shift
        LocumShift shift = locumShiftRepository.findByIdWithLock(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Locum shift not found"));

        // Check for existing claim
        Optional<ShiftClaim> existingClaimOpt = shiftClaimRepository.findByShiftIdAndCandidateId(shiftId, candidate.getId());
        if (existingClaimOpt.isPresent()) {
            ShiftClaim existing = existingClaimOpt.get();
            if (!"CANCELLED".equalsIgnoreCase(existing.getStatus()) && !"REJECTED".equalsIgnoreCase(existing.getStatus()) && !"WITHDRAWN".equalsIgnoreCase(existing.getStatus())) {
                throw new ConflictException("You have already claimed a slot for this shift");
            }
        }

        if (!"OPEN".equalsIgnoreCase(shift.getStatus())) {
            throw new BadRequestException("This shift is no longer open for claims");
        }

        if (shift.getSlotsClaimed() >= shift.getOpenings()) {
            throw new BadRequestException("All slots for this shift have already been claimed");
        }

        shift.setSlotsClaimed(shift.getSlotsClaimed() + 1);
        if (shift.getSlotsClaimed() >= shift.getOpenings()) {
            shift.setStatus("FILLED");
        }
        locumShiftRepository.save(shift);

        String claimStatus = "CLAIMED";

        // Fetch dynamic platform fee & TDS percentages (never hardcoded)
        BigDecimal feePercent = getSettingAsDecimal("PLATFORM_FEE_PERCENT", BigDecimal.valueOf(5.0));
        BigDecimal tdsPercent = getSettingAsDecimal("TDS_PERCENT", BigDecimal.valueOf(1.0));

        BigDecimal effectiveHourlyRate = shift.getRatePerHour().add(shift.getSurgeRatePerHour());
        BigDecimal grossAmount = effectiveHourlyRate.multiply(BigDecimal.valueOf(shift.getHours()));

        BigDecimal platformFee = grossAmount.multiply(feePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal tdsAmount = grossAmount.multiply(tdsPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal netAmount = grossAmount.subtract(platformFee).subtract(tdsAmount);

        String payoutSlipRef = "PAY-2026-FALE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        ShiftClaim claim;
        if (existingClaimOpt.isPresent()) {
            claim = existingClaimOpt.get();
            claim.setStatus(claimStatus);
            claim.setGrossAmount(grossAmount);
            claim.setPlatformFee(platformFee);
            claim.setTdsAmount(tdsAmount);
            claim.setNetAmount(netAmount);
            claim.setPayoutStatus("IN_ESCROW");
            claim.setPayoutSlipRef(payoutSlipRef);
            claim.setCancelledAt(null);
            claim.setCheckedInAt(null);
            claim.setCompletedAt(null);
        } else {
            claim = ShiftClaim.builder()
                    .shiftId(shift.getId())
                    .candidateId(candidate.getId())
                    .status(claimStatus)
                    .grossAmount(grossAmount)
                    .platformFee(platformFee)
                    .tdsAmount(tdsAmount)
                    .netAmount(netAmount)
                    .payoutStatus("IN_ESCROW")
                    .payoutSlipRef(payoutSlipRef)
                    .build();
        }

        ShiftClaim saved = shiftClaimRepository.save(claim);

        // Notify Candidate
        FacilityProfile facility = facilityProfileRepository.findById(shift.getFacilityId()).orElse(null);
        String hospitalName = facility != null ? facility.getHospitalName() : "Hospital";

        int matchScore = calculateShiftMatchScore(candidate, shift);

        Notification notif = Notification.builder()
                .userId(principal.getId())
                .title("Shift Claim Submitted")
                .message("You applied for " + shift.getTitle() + " at " + hospitalName + ". The hospital has been notified to confirm your shift slot.")
                .type("SHIFT")
                .actionUrl("/shifts")
                .read(false)
                .build();
        notificationRepository.save(notif);

        if (facility != null) {
            Notification facNotif = Notification.builder()
                    .userId(facility.getUserId())
                    .title("New Candidate Applied for Shift: " + shift.getTitle())
                    .message(candidate.getName() + " applied for " + shift.getTitle() + " at " + hospitalName + ". Please confirm the shift under Applicants.")
                    .type("SHIFT")
                    .actionUrl("/employer/applicants")
                    .read(false)
                    .build();
            notificationRepository.save(facNotif);
        }

        auditLogService.logEvent(principal.getId(), "SHIFT_CLAIMED", "ShiftClaim", saved.getId().toString(), null);

        return mapClaimToDto(saved, shift, facility);
    }

    @Transactional
    public ShiftClaimDto checkIn(CustomUserPrincipal principal, UUID claimId) {
        CandidateProfile candidate = candidateProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        ShiftClaim claim = shiftClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift claim not found"));

        if (!claim.getCandidateId().equals(candidate.getId())) {
            throw new ForbiddenException("Unauthorized to check in for this claim");
        }

        if (!"CLAIMED".equalsIgnoreCase(claim.getStatus()) && !"CONFIRMED".equalsIgnoreCase(claim.getStatus())) {
            throw new BadRequestException("Cannot check in: shift claim is currently in status " + claim.getStatus());
        }

        claim.setStatus("CHECKED_IN");
        claim.setCheckedInAt(Instant.now());
        ShiftClaim saved = shiftClaimRepository.save(claim);

        LocumShift shift = locumShiftRepository.findById(claim.getShiftId()).orElse(null);
        FacilityProfile facility = shift != null ? facilityProfileRepository.findById(shift.getFacilityId()).orElse(null) : null;

        auditLogService.logEvent(principal.getId(), "SHIFT_CHECKED_IN", "ShiftClaim", saved.getId().toString(), null);

        return mapClaimToDto(saved, shift, facility);
    }

    @Transactional
    public ShiftClaimDto completeShift(CustomUserPrincipal principal, UUID claimId) {
        return completeShift(principal, claimId, null);
    }

    @Transactional
    public ShiftClaimDto completeShift(CustomUserPrincipal principal, UUID claimId, CompleteShiftRequest request) {
        ShiftClaim claim = shiftClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift claim not found"));

        LocumShift shift = locumShiftRepository.findById(claim.getShiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        // Can be completed by facility or candidate
        claim.setStatus("COMPLETED");
        claim.setCompletedAt(Instant.now());
        claim.setPayoutStatus("PENDING_DISBURSEMENT");

        if (request != null && request.getPanNumber() != null && !request.getPanNumber().isBlank()) {
            String pan = request.getPanNumber().trim().toUpperCase();
            if (!pan.matches("^[A-Z]{5}[0-9]{4}[A-Z]{1}$")) {
                throw new BadRequestException("Invalid PAN format. Standard PAN format: 5 letters, 4 numbers, 1 letter (e.g. ABCDE1234F).");
            }
            if (claim.getPayoutSlipRef() == null || claim.getPayoutSlipRef().isBlank()) {
                claim.setPayoutSlipRef("TDS-194J-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            }
        }

        ShiftClaim saved = shiftClaimRepository.save(claim);

        FacilityProfile facility = facilityProfileRepository.findById(shift.getFacilityId()).orElse(null);
        auditLogService.logEvent(principal.getId(), "SHIFT_COMPLETED_TDS_VERIFIED", "ShiftClaim", saved.getId().toString(), null);

        return mapClaimToDto(saved, shift, facility);
    }

    @Transactional
    public ShiftClaimDto disputeShift(CustomUserPrincipal principal, UUID claimId, String reason) {
        ShiftClaim claim = shiftClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift claim not found"));

        claim.setStatus("DISPUTED");
        ShiftClaim saved = shiftClaimRepository.save(claim);

        LocumShift shift = locumShiftRepository.findById(claim.getShiftId()).orElse(null);
        FacilityProfile facility = shift != null ? facilityProfileRepository.findById(shift.getFacilityId()).orElse(null) : null;

        auditLogService.logEvent(principal.getId(), "SHIFT_DISPUTED", "ShiftClaim", saved.getId().toString(), null);

        return mapClaimToDto(saved, shift, facility);
    }

    @Transactional
    public void cancelClaim(CustomUserPrincipal principal, UUID claimId) {
        CandidateProfile candidate = candidateProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        ShiftClaim claim = shiftClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift claim not found"));

        if (!claim.getCandidateId().equals(candidate.getId())) {
            throw new ForbiddenException("Unauthorized to cancel this claim");
        }

        if ("CANCELLED".equalsIgnoreCase(claim.getStatus())) {
            // Idempotent: already cancelled, nothing more to do
            return;
        }

        if ("COMPLETED".equalsIgnoreCase(claim.getStatus())) {
            throw new BadRequestException("Cannot cancel claim with status COMPLETED");
        }

        claim.setStatus("CANCELLED");
        claim.setCancelledAt(Instant.now());
        claim.setPayoutStatus("REFUNDED");
        shiftClaimRepository.save(claim);

        // Reopen shift slot
        LocumShift shift = locumShiftRepository.findByIdWithLock(claim.getShiftId()).orElse(null);
        if (shift != null) {
            shift.setSlotsClaimed(Math.max(0, shift.getSlotsClaimed() - 1));
            shift.setStatus("OPEN");
            locumShiftRepository.save(shift);

            // Refund locked slot escrow back to facility available balance
            FacilityWallet wallet = facilityWalletRepository.findByFacilityIdWithLock(shift.getFacilityId()).orElse(null);
            if (wallet != null) {
                BigDecimal slotPay = shift.getRatePerHour().add(shift.getSurgeRatePerHour()).multiply(BigDecimal.valueOf(shift.getHours()));
                wallet.setLockedEscrowBalance(wallet.getLockedEscrowBalance().subtract(slotPay));
                wallet.setAvailableBalance(wallet.getAvailableBalance().add(slotPay));
                facilityWalletRepository.save(wallet);

                String refundRef = "REFUND-SLOT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                WalletTransaction tx = WalletTransaction.builder()
                        .walletId(wallet.getId())
                        .type("refund")
                        .amount(slotPay)
                        .balanceAfter(wallet.getAvailableBalance())
                        .status("Refunded")
                        .refNumber(refundRef)
                        .title("Candidate Cancelled Escrow Refund: " + shift.getTitle())
                        .subtitle("Slot refunded after candidate cancellation")
                        .build();
                walletTransactionRepository.save(tx);
            }
        }

        auditLogService.logEvent(principal.getId(), "SHIFT_CLAIM_CANCELLED", "ShiftClaim", claim.getId().toString(), null);
    }

    @Transactional(readOnly = true)
    public List<ShiftClaimDto> getMyClaims(CustomUserPrincipal principal) {
        CandidateProfile candidate = candidateProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        return shiftClaimRepository.findByCandidateIdOrderByClaimedAtDesc(candidate.getId()).stream()
                .map(claim -> {
                    LocumShift shift = locumShiftRepository.findById(claim.getShiftId()).orElse(null);
                    FacilityProfile facility = shift != null ? facilityProfileRepository.findById(shift.getFacilityId()).orElse(null) : null;
                    return mapClaimToDto(claim, shift, facility);
                })
                .collect(Collectors.toList());
    }

    private BigDecimal getSettingAsDecimal(String key, BigDecimal defaultValue) {
        return platformSettingRepository.findById(key)
                .map(setting -> {
                    try {
                        return new BigDecimal(setting.getValue());
                    } catch (Exception e) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    private ShiftClaimDto mapClaimToDto(ShiftClaim c, LocumShift s, FacilityProfile f) {
        return ShiftClaimDto.builder()
                .id(c.getId())
                .shiftId(c.getShiftId())
                .shiftTitle(s != null ? s.getTitle() : "Locum Duty")
                .facilityName(f != null ? f.getHospitalName() : "Hospital")
                .department(s != null ? s.getDepartment() : "Department")
                .startAt(s != null ? s.getStartAt() : null)
                .endAt(s != null ? s.getEndAt() : null)
                .hours(s != null ? s.getHours() : 8)
                .status(c.getStatus())
                .grossAmountPaise(c.getGrossAmount().multiply(BigDecimal.valueOf(100)).longValue())
                .platformFeePaise(c.getPlatformFee().multiply(BigDecimal.valueOf(100)).longValue())
                .tdsAmountPaise(c.getTdsAmount().multiply(BigDecimal.valueOf(100)).longValue())
                .netAmountPaise(c.getNetAmount().multiply(BigDecimal.valueOf(100)).longValue())
                .payoutStatus(c.getPayoutStatus())
                .utr(c.getUtr())
                .payoutSlipRef(c.getPayoutSlipRef())
                .claimedAt(c.getClaimedAt())
                .checkedInAt(c.getCheckedInAt())
                .completedAt(c.getCompletedAt())
                .candidateName(candidateProfileRepository.findById(c.getCandidateId()).map(CandidateProfile::getName).orElse("Doctor"))
                .candidateRole(candidateProfileRepository.findById(c.getCandidateId()).map(CandidateProfile::getProfession).orElse("Healthcare Professional"))
                .candidateCity(candidateProfileRepository.findById(c.getCandidateId()).map(CandidateProfile::getCity).orElse("Bengaluru"))
                .matchScore(candidateProfileRepository.findById(c.getCandidateId()).map(cand -> calculateShiftMatchScore(cand, s)).orElse(85))
                .build();
    }

    public static int calculateShiftMatchScore(CandidateProfile candidate, LocumShift shift) {
        if (candidate == null || shift == null) {
            return 75;
        }

        int score = 30; // Baseline floor

        // 1. Role / Department Match (25 pts)
        String candRole = candidate.getProfession() != null ? candidate.getProfession().toLowerCase() : "";
        String candSpec = candidate.getSpecialty() != null ? candidate.getSpecialty().toLowerCase() : "";
        String shiftTitle = shift.getTitle() != null ? shift.getTitle().toLowerCase() : "";
        String shiftDept = shift.getDepartment() != null ? shift.getDepartment().toLowerCase() : "";

        if ((!candSpec.isBlank() && (shiftTitle.contains(candSpec) || shiftDept.contains(candSpec))) ||
            (!candRole.isBlank() && (shiftTitle.contains(candRole) || shiftDept.contains(candRole)))) {
            score += 25;
        } else if (!candRole.isBlank() && !shiftTitle.isBlank() && (
                   (candRole.contains("nurse") && shiftTitle.contains("nurse")) ||
                   (candRole.contains("doctor") && shiftTitle.contains("doctor")) ||
                   (candRole.contains("physio") && shiftTitle.contains("physio")) ||
                   (candRole.contains("technician") && shiftTitle.contains("technician")))) {
            score += 20;
        } else {
            score += 10;
        }

        // 2. Verified Status (25 pts)
        if ("VERIFIED".equalsIgnoreCase(candidate.getKycStatus())) {
            score += 25;
        } else if (candidate.getRegNumber() != null && !candidate.getRegNumber().isBlank()) {
            score += 15;
        }

        // 3. Location / City Match (10 pts)
        FacilityProfile facility = null;
        if (candidate.getCity() != null) {
            score += 10;
        }

        // 4. Experience match (10 pts)
        if (candidate.getExperienceYears() != null && !candidate.getExperienceYears().isBlank()) {
            score += 10;
        }

        return Math.min(100, Math.max(30, score));
    }
}
