package com.thefale.service;

import com.thefale.dto.CompleteShiftRequest;
import com.thefale.dto.RateShiftClaimRequest;
import com.thefale.dto.ShiftCheckInRequest;
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
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftClaimService {

    private static final SecureRandom PIN_RANDOM = new SecureRandom();
    private static final Set<String> NON_FINAL_REOPEN_STATUSES = Set.of(
            "CANCELLED", "WITHDRAWN", "REJECTED", "SLOT_FILLED", "NO_SHOW"
    );

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
    private final CandidateDocumentRepository candidateDocumentRepository;

    @Transactional
    public ShiftClaimDto claimShift(CustomUserPrincipal principal, UUID shiftId) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User account not found"));

        if ("STUDENT".equalsIgnoreCase(principal.getCandidateLevel()) || "STUDENT".equalsIgnoreCase(user.getCandidateLevel())) {
            throw new ForbiddenException("Healthcare students are not permitted to claim clinical locum shifts.");
        }

        CandidateProfile candidate = candidateProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ForbiddenException("Complete your clinical profile and council KYC verification before claiming locum shifts."));

        if (!"VERIFIED".equalsIgnoreCase(candidate.getKycStatus())) {
            throw new ForbiddenException("Your council/KYC verification status is " + candidate.getKycStatus() + ". Only VERIFIED clinicians can claim locum shifts.");
        }

        LocumShift shift = locumShiftRepository.findByIdWithLock(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Locum shift not found"));

        if (!"OPEN".equalsIgnoreCase(shift.getStatus())) {
            throw new BadRequestException("This shift is no longer open for claims");
        }
        if (shift.getEndAt() != null && !shift.getEndAt().isAfter(Instant.now())) {
            throw new BadRequestException("This shift's duty window has already ended and can no longer be claimed.");
        }
        if (shift.getSlotsClaimed() >= shift.getOpenings()) {
            throw new BadRequestException("All slots for this shift have already been claimed");
        }

        // Check for existing claim on this shift
        Optional<ShiftClaim> existingClaimOpt = shiftClaimRepository.findByShiftIdAndCandidateId(shiftId, candidate.getId());
        if (existingClaimOpt.isPresent()) {
            ShiftClaim existing = existingClaimOpt.get();
            if ("REJECTED".equalsIgnoreCase(existing.getStatus())) {
                throw new BadRequestException("Your previous claim for this shift was declined by the facility. You cannot re-apply for this same shift.");
            }
            if ("SLOT_FILLED".equalsIgnoreCase(existing.getStatus())) {
                throw new BadRequestException("All openings for this shift have been filled.");
            }
            if (!"CANCELLED".equalsIgnoreCase(existing.getStatus()) && !"WITHDRAWN".equalsIgnoreCase(existing.getStatus())) {
                throw new ConflictException("You have already claimed a slot for this shift");
            }
        }

        // Prevent overlapping double-booked shift claims
        List<ShiftClaim> activeClaims = shiftClaimRepository.findActiveClaimsByCandidate(candidate.getId());
        for (ShiftClaim otherClaim : activeClaims) {
            if (otherClaim.getShiftId().equals(shiftId)) continue;
            LocumShift otherShift = locumShiftRepository.findById(otherClaim.getShiftId()).orElse(null);
            if (otherShift != null && shift.getStartAt() != null && shift.getEndAt() != null
                    && otherShift.getStartAt() != null && otherShift.getEndAt() != null) {
                boolean overlaps = shift.getStartAt().isBefore(otherShift.getEndAt())
                        && shift.getEndAt().isAfter(otherShift.getStartAt());
                if (overlaps) {
                    throw new ConflictException("Schedule conflict: you already have an active shift (" + otherShift.getTitle() + ") overlapping this time window.");
                }
            }
        }

        ShiftRules.checkEligibility(candidate, shift.getDetails(), candidateDocumentRepository.findByCandidateId(candidate.getId()));

        // Candidate 1st-shift fee waiver: 0% platform fee on candidate's 1st valid claim, 5% from 2nd+ shift onward
        long priorValidClaims = shiftClaimRepository.countValidFeeClaimsByCandidate(candidate.getId());
        BigDecimal standardFeePct = getSettingAsDecimal("PLATFORM_FEE_PERCENT", BigDecimal.valueOf(5.0));
        BigDecimal feePercent = BigDecimal.ZERO;
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
            claim.setStatus("CLAIMED");
            claim.setGrossAmount(grossAmount);
            claim.setPlatformFeePercent(feePercent);
            claim.setPlatformFee(platformFee);
            claim.setTdsAmount(tdsAmount);
            claim.setNetAmount(netAmount);
            claim.setPayoutStatus("IN_ESCROW");
            claim.setPayoutSlipRef(payoutSlipRef);
            claim.setCheckInPin(null);
            claim.setDisputeReason(null);
            claim.setDisputedAt(null);
            claim.setDisputedBy(null);
            claim.setCancelledAt(null);
            claim.setCheckedInAt(null);
            claim.setCompletedAt(null);
        } else {
            claim = ShiftClaim.builder()
                    .shiftId(shift.getId())
                    .candidateId(candidate.getId())
                    .status("CLAIMED")
                    .grossAmount(grossAmount)
                    .platformFeePercent(feePercent)
                    .platformFee(platformFee)
                    .tdsAmount(tdsAmount)
                    .netAmount(netAmount)
                    .payoutStatus("IN_ESCROW")
                    .payoutSlipRef(payoutSlipRef)
                    .build();
        }

        ShiftClaim saved = shiftClaimRepository.save(claim);

        FacilityProfile facility = facilityProfileRepository.findById(shift.getFacilityId()).orElse(null);
        String hospitalName = facility != null ? facility.getHospitalName() : "Hospital";

        String feeNote = feePercent.compareTo(BigDecimal.ZERO) == 0
                ? " No clinician platform fee. Applicable TDS is shown separately."
                : " Platform fee: " + feePercent.stripTrailingZeros().toPlainString() + "% + 1% TDS.";
        notificationRepository.save(Notification.builder()
                .userId(principal.getId())
                .title("Shift Claim Submitted")
                .message("You applied for " + shift.getTitle() + " at " + hospitalName + "." + feeNote + " Waiting for hospital confirmation.")
                .type("SHIFT")
                .actionUrl("/shifts")
                .read(false)
                .build());

        if (facility != null) {
            notificationRepository.save(Notification.builder()
                    .userId(facility.getUserId())
                    .title("New Verified Clinician Applied: " + shift.getTitle())
                    .message(candidate.getName() + " applied for " + shift.getTitle() + " at " + hospitalName + ". Confirm the slot under Applicants to issue their check-in PIN.")
                    .type("SHIFT")
                    .actionUrl("/employer/applicants")
                    .read(false)
                    .build());
        }

        auditLogService.logEvent(principal.getId(), "SHIFT_CLAIMED", "ShiftClaim", saved.getId().toString(), null);
        return mapClaimToDto(saved, shift, facility);
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public ShiftClaimDto checkIn(CustomUserPrincipal principal, UUID claimId, ShiftCheckInRequest request) {
        CandidateProfile candidate = candidateProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        ShiftClaim claim = shiftClaimRepository.findByIdWithLock(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift claim not found"));

        if (!claim.getCandidateId().equals(candidate.getId())) {
            throw new ForbiddenException("Unauthorized to check in for this claim");
        }

        if (!"CONFIRMED".equalsIgnoreCase(claim.getStatus())) {
            throw new BadRequestException("Cannot check in: hospital must confirm your shift slot first (current status: " + claim.getStatus() + ")");
        }

        LocumShift shift = locumShiftRepository.findById(claim.getShiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Locum shift not found"));

        Instant now = Instant.now();
        if (shift.getStartAt() != null && now.isBefore(shift.getStartAt().minus(2, ChronoUnit.HOURS))) {
            throw new BadRequestException("Check-in opens 2 hours before the scheduled shift start time.");
        }
        if (shift.getEndAt() != null && now.isAfter(shift.getEndAt().plus(2, ChronoUnit.HOURS))) {
            throw new BadRequestException("The check-in window for this shift has closed.");
        }

        validateAttendance(claim, request, now);
        claim.setCheckInLatitude(request.getLatitude()); claim.setCheckInLongitude(request.getLongitude());
        claim.setCheckInAccuracy(request.getAccuracy()); claim.setLocationCapturedAt(request.getLocationCapturedAt());
        claim.setSelfieCapturedAt(request.getSelfieCapturedAt()); claim.setCheckInSelfie(request.getSelfieDataUrl());
        claim.setCheckInMethod(request.getMethod()); claim.setCheckInQrToken(null); claim.setCheckInPin(null);
        claim.setStatus("CHECKED_IN");
        claim.setCheckedInAt(now);
        ShiftClaim saved = shiftClaimRepository.save(claim);

        FacilityProfile facility = facilityProfileRepository.findById(shift.getFacilityId()).orElse(null);
        if (facility != null) {
            notificationRepository.save(Notification.builder()
                    .userId(facility.getUserId())
                    .title("Clinician Checked In: " + shift.getTitle())
                    .message(candidate.getName() + " checked in on floor for " + shift.getTitle() + ".")
                    .type("SHIFT")
                    .actionUrl("/employer/applicants")
                    .read(false)
                    .build());
        }

        auditLogService.logEvent(principal.getId(), "SHIFT_CHECKED_IN", "ShiftClaim", saved.getId().toString(), null);
        return mapClaimToDto(saved, shift, facility);
    }

    @Transactional
    public ShiftClaimDto completeShift(CustomUserPrincipal principal, UUID claimId) {
        return completeShift(principal, claimId, null);
    }

    @Transactional
    public ShiftClaimDto completeShift(CustomUserPrincipal principal, UUID claimId, CompleteShiftRequest request) {
        ShiftClaim claim = shiftClaimRepository.findByIdWithLock(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift claim not found"));

        LocumShift shift = locumShiftRepository.findByIdWithLock(claim.getShiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        boolean isAdmin = principal != null && principal.isAdmin();
        boolean isFacility = principal != null && (principal.isFacility() || isAdmin);
        int scheduledHours = shift.getHours() > 0 ? shift.getHours() : 8;
        int hoursClaimed = (request != null && request.getActualHours() != null && request.getActualHours() > 0)
                ? request.getActualHours()
                : (claim.getActualHours() != null && claim.getActualHours() > 0 ? claim.getActualHours() : scheduledHours);

        if (hoursClaimed < 1 || hoursClaimed > scheduledHours + 4) {
            throw new BadRequestException("Reported hours must be between 1 and " + (scheduledHours + 4) + " hours.");
        }

        if (!isFacility) {
            CandidateProfile candidate = candidateProfileRepository.findByUserId(principal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));
            if (!claim.getCandidateId().equals(candidate.getId())) {
                throw new ForbiddenException("Unauthorized to submit timesheet for this claim");
            }
            if (!"CHECKED_IN".equalsIgnoreCase(claim.getStatus()) && !"IN_PROGRESS".equalsIgnoreCase(claim.getStatus())) {
                throw new BadRequestException("Please check in with your shift PIN before signing off duty (current status: " + claim.getStatus() + ").");
            }

            claim.setActualHours(hoursClaimed);
            claim.setStatus("TIMESHEET_SUBMITTED");
            ShiftClaim saved = shiftClaimRepository.save(claim);

            FacilityProfile facility = facilityProfileRepository.findById(shift.getFacilityId()).orElse(null);
            if (facility != null) {
                notificationRepository.save(Notification.builder()
                        .userId(facility.getUserId())
                        .title("Timesheet Submitted: " + shift.getTitle())
                        .message(candidate.getName() + " reported " + hoursClaimed + " hrs duty for " + shift.getTitle() + ". Verify timesheet to finalize escrow.")
                        .type("SHIFT_TIMESHEET")
                        .actionUrl("/employer/applicants")
                        .read(false)
                        .build());
            }

            auditLogService.logEvent(principal.getId(), "SHIFT_TIMESHEET_SUBMITTED", "ShiftClaim", saved.getId().toString(), null);
            return mapClaimToDto(saved, shift, facility);
        }

        // Facility / Admin verifying timesheet -> Ownership + state check
        if (!isAdmin) {
            FacilityProfile callerFacility = facilityProfileRepository.findByUserId(principal.getId())
                    .orElseThrow(() -> new ForbiddenException("Facility profile not found"));
            if (!callerFacility.getId().equals(shift.getFacilityId())) {
                throw new ForbiddenException("Unauthorized to verify shift claim belonging to another facility");
            }
        }

        if ("COMPLETED".equalsIgnoreCase(claim.getStatus()) || "DISBURSED".equalsIgnoreCase(claim.getPayoutStatus())) {
            throw new BadRequestException("This shift timesheet has already been verified and completed.");
        }
        if (!Set.of("TIMESHEET_SUBMITTED").contains(claim.getStatus().toUpperCase())) {
            throw new BadRequestException("Cannot verify timesheet when claim is in status " + claim.getStatus());
        }

        if (request != null && request.getPanNumber() != null && !request.getPanNumber().isBlank()) {
            String pan = request.getPanNumber().trim().toUpperCase();
            if (!pan.matches("^[A-Z]{5}[0-9]{4}[A-Z]{1}$")) {
                throw new BadRequestException("Invalid PAN format. Standard PAN format: 5 letters, 4 numbers, 1 letter (e.g. ABCDE1234F).");
            }
            if (claim.getPayoutSlipRef() == null || claim.getPayoutSlipRef().isBlank()) {
                claim.setPayoutSlipRef("TDS-194J-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            }
        }

        // The hospital pays the platform fee; clinicians have no platform deduction.
        BigDecimal finalCandFeePct = BigDecimal.ZERO;
        BigDecimal tdsPercent = getSettingAsDecimal("TDS_PERCENT", BigDecimal.valueOf(1.0));

        BigDecimal effectiveHourlyRate = shift.getRatePerHour().add(shift.getSurgeRatePerHour() != null ? shift.getSurgeRatePerHour() : BigDecimal.ZERO);
        BigDecimal scheduledGross = effectiveHourlyRate.multiply(BigDecimal.valueOf(scheduledHours));
        BigDecimal newGross = effectiveHourlyRate.multiply(BigDecimal.valueOf(hoursClaimed));

        BigDecimal platformFee = newGross.multiply(finalCandFeePct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal tdsAmount = newGross.multiply(tdsPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal netAmount = newGross.subtract(platformFee).subtract(tdsAmount);

        claim.setPlatformFeePercent(finalCandFeePct);
        claim.setGrossAmount(newGross);
        claim.setPlatformFee(platformFee);
        claim.setTdsAmount(tdsAmount);
        claim.setNetAmount(netAmount);
        claim.setStatus("COMPLETED");
        claim.setCompletedAt(Instant.now());
        claim.setPayoutStatus("PENDING_DISBURSEMENT");
        claim.setActualHours(hoursClaimed);

        // Wallet Escrow Reconciliation (worker gross delta + proportional facility service fee delta)
        BigDecimal facFeePct = shift.getFacilityFeePercent() != null ? shift.getFacilityFeePercent() : BigDecimal.ZERO;
        BigDecimal scheduledSlotFacFee = ShiftCharges.percent(scheduledGross, facFeePct);
        BigDecimal scheduledSlotGst = ShiftCharges.gst(scheduledSlotFacFee, shift);
        FacilityWallet settlementWallet = facilityWalletRepository.findByFacilityIdWithLock(shift.getFacilityId())
                .orElseThrow(() -> new BadRequestException("Hospital wallet is required for settlement."));
        if (settlementWallet.getLockedEscrowBalance().compareTo(scheduledGross.add(scheduledSlotFacFee).add(scheduledSlotGst)) < 0)
            throw new BadRequestException("Escrow reserve mismatch. Contact support before settling this duty.");
        if (settlementWallet.getFirstCompletedShiftId() == null) settlementWallet.setFirstCompletedShiftId(shift.getId());
        boolean waived = shift.getId().equals(settlementWallet.getFirstCompletedShiftId());
        facilityWalletRepository.save(settlementWallet);
        BigDecimal newSlotFacFee = waived ? BigDecimal.ZERO : newGross.multiply(facFeePct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        claim.setHospitalFeeAmount(newSlotFacFee);
        BigDecimal newSlotGst = ShiftCharges.gst(newSlotFacFee, shift);
        claim.setHospitalFeeGstAmount(newSlotGst);

        BigDecimal scheduledSlotTotalEscrow = scheduledGross.add(scheduledSlotFacFee).add(scheduledSlotGst);
        BigDecimal verifiedSlotTotalEscrow = newGross.add(newSlotFacFee).add(newSlotGst);
        BigDecimal escrowDelta = verifiedSlotTotalEscrow.subtract(scheduledSlotTotalEscrow);

        if (escrowDelta.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal refundAmount = escrowDelta.abs();
            facilityWalletRepository.findByFacilityIdWithLock(shift.getFacilityId()).ifPresent(wallet -> {
                BigDecimal safeSub = refundAmount.min(wallet.getLockedEscrowBalance());
                wallet.setLockedEscrowBalance(wallet.getLockedEscrowBalance().subtract(safeSub));
                wallet.setAvailableBalance(wallet.getAvailableBalance().add(refundAmount));
                facilityWalletRepository.save(wallet);

                walletTransactionRepository.save(WalletTransaction.builder()
                        .walletId(wallet.getId())
                        .type("refund")
                        .amount(refundAmount)
                        .platformFeeAmount(scheduledSlotFacFee.subtract(newSlotFacFee))
                        .platformFeeGstAmount(scheduledSlotGst.subtract(newSlotGst))
                        .balanceAfter(wallet.getAvailableBalance())
                        .status("Completed")
                        .invoiceId(DocumentReferences.next("CRN"))
                        .refNumber("ESCROW-REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .title("Timesheet Verification Refund: " + shift.getTitle())
                        .subtitle((scheduledHours - hoursClaimed) + " unused shift hours refunded (" + hoursClaimed + "h verified vs " + scheduledHours + "h scheduled)")
                        .build());
            });

        } else if (escrowDelta.compareTo(BigDecimal.ZERO) > 0) {
            FacilityWallet wallet = facilityWalletRepository.findByFacilityIdWithLock(shift.getFacilityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Facility wallet not found"));
            if (wallet.getAvailableBalance().compareTo(escrowDelta) < 0) {
                throw new BadRequestException("Insufficient wallet balance to cover overtime escrow (" + (hoursClaimed - scheduledHours) + " extra hrs). Additional escrow needed: ₹" + escrowDelta);
            }
            wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(escrowDelta));
            wallet.setLockedEscrowBalance(wallet.getLockedEscrowBalance().add(escrowDelta));
            facilityWalletRepository.save(wallet);

            walletTransactionRepository.save(WalletTransaction.builder()
                    .walletId(wallet.getId())
                    .type("escrow_hold")
                    .amount(escrowDelta.negate())
                    .platformFeeAmount(newSlotFacFee.subtract(scheduledSlotFacFee))
                    .platformFeeGstAmount(newSlotGst.subtract(scheduledSlotGst))
                    .balanceAfter(wallet.getAvailableBalance())
                    .status("In Escrow")
                    .refNumber("ESCROW-OT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .title("Overtime Escrow Lock: " + shift.getTitle())
                    .subtitle((hoursClaimed - scheduledHours) + " overtime hour(s) verified")
                    .build());


        }

        shift.setFacilityFeeAmount(ShiftCharges.zero(shift.getFacilityFeeAmount()).add(newSlotFacFee).subtract(scheduledSlotFacFee).max(BigDecimal.ZERO));
        shift.setPlatformFeeGstAmount(ShiftCharges.reservedGst(shift).add(newSlotGst).subtract(scheduledSlotGst).max(BigDecimal.ZERO));
        locumShiftRepository.save(shift);

        if (claim.getPayoutSlipRef() == null || claim.getPayoutSlipRef().isBlank()) claim.setPayoutSlipRef(DocumentReferences.payout(claim.getId()));
        SettlementRecords.save(walletTransactionRepository, settlementWallet, claim, shift, verifiedSlotTotalEscrow, newSlotFacFee, newSlotGst, false);

        ShiftClaim saved = shiftClaimRepository.save(claim);

        FacilityProfile facility = facilityProfileRepository.findById(shift.getFacilityId()).orElse(null);
        CandidateProfile candidate = candidateProfileRepository.findById(claim.getCandidateId()).orElse(null);
        if (candidate != null) {
            candidate.setCompletedShiftsCount((candidate.getCompletedShiftsCount() != null ? candidate.getCompletedShiftsCount() : 0) + 1);
            candidateProfileRepository.save(candidate);

            String hosp = facility != null ? facility.getHospitalName() : "Hospital Partner";
            notificationRepository.save(Notification.builder()
                    .userId(candidate.getUserId())
                    .title("Timesheet Verified — Escrow Auto-Release")
                    .message(hosp + " approved your duty hours for " + shift.getTitle() + " (" + hoursClaimed + " hrs). Net payout of ₹" + claim.getNetAmount() + " is queued for disbursement.")
                    .type("PAYOUT")
                    .actionUrl("/earnings")
                    .read(false)
                    .build());
        }

        auditLogService.logEvent(principal.getId(), "SHIFT_COMPLETED_TDS_VERIFIED", "ShiftClaim", saved.getId().toString(), null);
        return mapClaimToDto(saved, shift, facility);
    }

    @Transactional
    public ShiftClaimDto rateShiftClaim(CustomUserPrincipal principal, UUID claimId, RateShiftClaimRequest request) {
        FacilityProfile facility = facilityProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ForbiddenException("Facility profile not found"));

        ShiftClaim claim = shiftClaimRepository.findByIdWithLock(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift claim not found"));

        LocumShift shift = locumShiftRepository.findById(claim.getShiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        if (!shift.getFacilityId().equals(facility.getId())) {
            throw new ForbiddenException("Unauthorized to rate a shift belonging to another facility");
        }

        if (!"COMPLETED".equalsIgnoreCase(claim.getStatus())) {
            throw new BadRequestException("You can only rate clinicians after the shift is completed and timesheet is verified.");
        }

        if (claim.getHospitalRating() != null) {
            throw new BadRequestException("You have already submitted a rating for this completed shift.");
        }

        int rating = request.getRating();
        String review = request.getReview() != null ? request.getReview().trim() : "";
        if (rating <= 2 && review.isBlank()) {
            throw new BadRequestException("Please provide brief feedback explaining why the rating is 2 stars or below.");
        }

        String tags = request.getTags() != null && !request.getTags().isEmpty()
                ? request.getTags().stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.joining(", "))
                : null;

        claim.setHospitalRating(rating);
        claim.setHospitalReview(review.isBlank() ? null : review);
        claim.setRatingTags(tags);
        claim.setRatedAt(Instant.now());
        ShiftClaim saved = shiftClaimRepository.save(claim);

        // Recompute candidate aggregate ratings
        CandidateProfile candidate = candidateProfileRepository.findById(claim.getCandidateId()).orElse(null);
        if (candidate != null) {
            List<ShiftClaim> rated = shiftClaimRepository.findRatedClaimsByCandidate(candidate.getId());
            int count = rated.size();
            double sum = rated.stream().mapToInt(c -> c.getHospitalRating() != null ? c.getHospitalRating() : 0).sum();
            BigDecimal avg = count > 0
                    ? BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            candidate.setShiftRatingCount(count);
            candidate.setShiftRatingAvg(avg);
            candidateProfileRepository.save(candidate);

            notificationRepository.save(Notification.builder()
                    .userId(candidate.getUserId())
                    .title("New Hospital Shift Rating: " + rating + "★")
                    .message(facility.getHospitalName() + " rated your " + shift.getTitle() + " duty " + rating + "/5 stars. High ratings boost your shift match rank & priority alerts.")
                    .type("SHIFT")
                    .actionUrl("/profile")
                    .read(false)
                    .build());
        }

        auditLogService.logEvent(principal.getId(), "SHIFT_CLINICIAN_RATED", "ShiftClaim", saved.getId().toString(), null);
        return mapClaimToDto(saved, shift, facility);
    }

    @Transactional
    public ShiftClaimDto disputeShift(CustomUserPrincipal principal, UUID claimId, String reason) {
        ShiftClaim claim = shiftClaimRepository.findByIdWithLock(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift claim not found"));

        LocumShift shift = locumShiftRepository.findById(claim.getShiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found"));

        boolean isAdmin = principal != null && principal.isAdmin();
        boolean isFacility = principal != null && principal.isFacility();

        if (!isAdmin) {
            if (isFacility) {
                FacilityProfile fac = facilityProfileRepository.findByUserId(principal.getId())
                        .orElseThrow(() -> new ForbiddenException("Facility profile not found"));
                if (!fac.getId().equals(shift.getFacilityId())) {
                    throw new ForbiddenException("Unauthorized to dispute a shift belonging to another facility");
                }
            } else {
                CandidateProfile cand = candidateProfileRepository.findByUserId(principal.getId())
                        .orElseThrow(() -> new ForbiddenException("Candidate profile not found"));
                if (!cand.getId().equals(claim.getCandidateId())) {
                    throw new ForbiddenException("Unauthorized to dispute another clinician's claim");
                }
            }
        }

        if ("DISBURSED".equalsIgnoreCase(claim.getPayoutStatus())) {
            throw new BadRequestException("Cannot dispute a shift whose payout has already been disbursed.");
        }
        if (NON_FINAL_REOPEN_STATUSES.contains(claim.getStatus().toUpperCase()) || "DISPUTED".equalsIgnoreCase(claim.getStatus())) {
            throw new BadRequestException("Cannot open dispute for claim in status " + claim.getStatus());
        }

        String cleanReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Hours or duty discrepancy raised for admin mediation.";
        claim.setStatus("DISPUTED");
        claim.setDisputeReason(cleanReason);
        claim.setDisputedAt(Instant.now());
        claim.setDisputedBy(isAdmin ? "ADMIN" : isFacility ? "FACILITY" : "CANDIDATE");
        ShiftClaim saved = shiftClaimRepository.save(claim);

        FacilityProfile facility = facilityProfileRepository.findById(shift.getFacilityId()).orElse(null);
        CandidateProfile candidate = candidateProfileRepository.findById(claim.getCandidateId()).orElse(null);

        if (isFacility && candidate != null) {
            notificationRepository.save(Notification.builder()
                    .userId(candidate.getUserId())
                    .title("Shift Dispute Opened: " + shift.getTitle())
                    .message((facility != null ? facility.getHospitalName() : "Hospital") + " flagged a dispute for Admin review: " + cleanReason)
                    .type("SHIFT")
                    .actionUrl("/shifts")
                    .read(false)
                    .build());
        } else if (!isFacility && facility != null) {
            notificationRepository.save(Notification.builder()
                    .userId(facility.getUserId())
                    .title("Clinician Raised Shift Dispute: " + shift.getTitle())
                    .message((candidate != null ? candidate.getName() : "Clinician") + " raised a dispute for Admin review: " + cleanReason)
                    .type("SHIFT")
                    .actionUrl("/employer/applicants")
                    .read(false)
                    .build());
        }

        auditLogService.logEvent(principal.getId(), "SHIFT_DISPUTED", "ShiftClaim", saved.getId().toString(), null);
        return mapClaimToDto(saved, shift, facility);
    }

    @Transactional
    public void cancelClaim(CustomUserPrincipal principal, UUID claimId) {
        CandidateProfile candidate = candidateProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));

        ShiftClaim claim = shiftClaimRepository.findByIdWithLock(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift claim not found"));

        if (!claim.getCandidateId().equals(candidate.getId())) {
            throw new ForbiddenException("Unauthorized to cancel this claim");
        }

        if ("CANCELLED".equalsIgnoreCase(claim.getStatus())) {
            return;
        }

        if (!Set.of("CLAIMED", "CLAIM_PENDING_ADMIN", "CONFIRMED").contains(claim.getStatus().toUpperCase())) {
            throw new BadRequestException("Cannot cancel claim in status " + claim.getStatus());
        }

        LocumShift shift = locumShiftRepository.findByIdWithLock(claim.getShiftId()).orElse(null);
        if (shift != null && shift.getStartAt() != null && !Instant.now().isBefore(shift.getStartAt())) {
            throw new BadRequestException("Cancellation cutoff passed: claims cannot be withdrawn after the shift has started.");
        }

        claim.setStatus("CANCELLED");
        claim.setCancelledAt(Instant.now());
        claim.setPayoutStatus("REFUNDED");
        claim.setCheckInPin(null);
        shiftClaimRepository.save(claim);

        // Reopen shift slot cleanly (keep shift-level escrow locked so the reopened opening stays funded!)
        if (shift != null && Set.of("OPEN", "FILLED").contains(shift.getStatus().toUpperCase())) {
            long activeCount = shiftClaimRepository.findByShiftId(shift.getId()).stream()
                    .filter(ShiftRules::reservesSlot)
                    .count();
            shift.setSlotsClaimed((int) activeCount);
            if (activeCount < shift.getOpenings()) {
                shift.setStatus("OPEN");
            }
            locumShiftRepository.save(shift);

            facilityProfileRepository.findById(shift.getFacilityId()).ifPresent(fac ->
                    notificationRepository.save(Notification.builder()
                            .userId(fac.getUserId())
                            .title("Clinician Withdrew From Shift: " + shift.getTitle())
                            .message(candidate.getName() + " cancelled before shift start. Slot is reopened for other verified clinicians.")
                            .type("SHIFT")
                            .actionUrl("/employer/postings")
                            .read(false)
                            .build())
            );
        }

        auditLogService.logEvent(principal.getId(), "SHIFT_CLAIM_CANCELLED", "ShiftClaim", claim.getId().toString(), null);
    }

    @Transactional(readOnly = true)
    public ShiftClaimDto getClaimForPayoutSlip(CustomUserPrincipal principal, UUID claimId) {
        CandidateProfile candidate = candidateProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found"));
        ShiftClaim claim = shiftClaimRepository.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift payout claim not found"));
        if (!claim.getCandidateId().equals(candidate.getId())) {
            throw new ForbiddenException("Unauthorized to access this payout slip");
        }
        if (!"DISBURSED".equalsIgnoreCase(claim.getPayoutStatus()) && !("COMPLETED".equalsIgnoreCase(claim.getStatus()) && "PENDING_DISBURSEMENT".equalsIgnoreCase(claim.getPayoutStatus())))
            throw new BadRequestException("A payout advice is available only after the hospital approves the timesheet.");
        LocumShift shift = locumShiftRepository.findById(claim.getShiftId()).orElse(null);
        FacilityProfile facility = shift != null ? facilityProfileRepository.findById(shift.getFacilityId()).orElse(null) : null;
        return mapClaimToDto(claim, shift, facility);
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

    public void verifyCandidateForConfirmation(UUID candidateId, LocumShift shift) {
        CandidateProfile candidate = candidateProfileRepository.findByIdWithLock(candidateId)
                .orElseThrow(() -> new BadRequestException("Candidate profile is missing."));
        if (!"VERIFIED".equalsIgnoreCase(candidate.getKycStatus())) throw new BadRequestException("Candidate verification is required.");
        ShiftRules.checkEligibility(candidate, shift.getDetails(), candidateDocumentRepository.findByCandidateId(candidateId));
        for (ShiftClaim other : shiftClaimRepository.findActiveClaimsByCandidate(candidateId)) {
            if (other.getShiftId().equals(shift.getId())) continue;
            LocumShift existing = locumShiftRepository.findById(other.getShiftId()).orElse(null);
            if (existing != null && shift.getStartAt().isBefore(existing.getEndAt()) && shift.getEndAt().isAfter(existing.getStartAt()))
                throw new BadRequestException("Candidate already has a confirmed overlapping shift.");
        }
    }

    private void validateAttendance(ShiftClaim claim, ShiftCheckInRequest request, Instant now) {
        if (claim.getCheckInBlockedUntil() != null && now.isBefore(claim.getCheckInBlockedUntil()))
            throw new BadRequestException("Too many incorrect check-in attempts. Try again in five minutes.");
        ShiftAttendanceValidator.validate(request, now);
        boolean pin = "PIN".equals(request.getMethod()) && request.getPin() != null && request.getPin().matches("[0-9]{6}")
                && claim.getCheckInPin() != null && claim.getCheckInPin().equals(request.getPin());
        boolean qr = "QR".equals(request.getMethod()) && claim.getCheckInQrToken() != null
                && claim.getCheckInQrToken().equals(request.getQrToken());
        if (!pin && !qr) {
            if (claim.getCheckInBlockedUntil() != null && !now.isBefore(claim.getCheckInBlockedUntil())) claim.setCheckInFailedAttempts(0);
            claim.setCheckInFailedAttempts(claim.getCheckInFailedAttempts() + 1);
            if (claim.getCheckInFailedAttempts() >= 5) claim.setCheckInBlockedUntil(now.plusSeconds(300));
            shiftClaimRepository.save(claim);
            throw new BadRequestException("Invalid six-digit PIN or QR. Ask the hospital duty supervisor for the correct credential.");
        }
        claim.setCheckInFailedAttempts(0); claim.setCheckInBlockedUntil(null);
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> attendance(CustomUserPrincipal principal, UUID claimId) {
        ShiftClaim claim = shiftClaimRepository.findById(claimId).orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
        LocumShift shift = locumShiftRepository.findById(claim.getShiftId()).orElseThrow(() -> new ResourceNotFoundException("Shift not found"));
        boolean owner = candidateProfileRepository.findByUserId(principal.getId()).map(c -> c.getId().equals(claim.getCandidateId())).orElse(false);
        boolean hospital = facilityProfileRepository.findByUserId(principal.getId()).map(f -> f.getId().equals(shift.getFacilityId())).orElse(false);
        if (!principal.isAdmin() && !owner && !hospital) throw new ForbiddenException("You cannot view this attendance record.");
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("latitude", claim.getCheckInLatitude()); result.put("longitude", claim.getCheckInLongitude());
        result.put("accuracy", claim.getCheckInAccuracy()); result.put("checkedInAt", claim.getCheckedInAt());
        result.put("method", claim.getCheckInMethod()); result.put("selfieDataUrl", claim.getCheckInSelfie());
        return result;
    }

    public static String generateCheckInPin() {
        return String.format("%06d", 100000 + PIN_RANDOM.nextInt(900000));
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

    public ShiftClaimDto mapClaimToDto(ShiftClaim c, LocumShift s, FacilityProfile f) {
        CandidateProfile cand = candidateProfileRepository.findById(c.getCandidateId()).orElse(null);
        double feePct = c.getPlatformFeePercent() != null ? c.getPlatformFeePercent().doubleValue() : 0.0;
        return ShiftClaimDto.builder()
                .id(c.getId())
                .shiftId(c.getShiftId())
                .candidateId(c.getCandidateId())
                .shiftTitle(s != null ? s.getTitle() : "Locum Duty")
                .facilityName(f != null ? f.getHospitalName() : "Hospital")
                .facilityCity(f != null ? f.getCity() : null)
                .department(s != null ? s.getDepartment() : "Department")
                .startAt(s != null ? s.getStartAt() : null)
                .endAt(s != null ? s.getEndAt() : null)
                .hours(s != null ? s.getHours() : 8)
                .actualHours(c.getActualHours())
                .status(c.getStatus())
                .grossAmountPaise(c.getGrossAmount().multiply(BigDecimal.valueOf(100)).longValue())
                .platformFeePaise(c.getPlatformFee().multiply(BigDecimal.valueOf(100)).longValue())
                .tdsAmountPaise(c.getTdsAmount().multiply(BigDecimal.valueOf(100)).longValue())
                .netAmountPaise(c.getNetAmount().multiply(BigDecimal.valueOf(100)).longValue())
                .platformFeePercent(feePct)
                .firstShiftFree(false)
                .payoutStatus(c.getPayoutStatus())
                .utr(c.getUtr())
                .payoutSlipRef(c.getPayoutSlipRef())
                .disbursedAt(c.getDisbursedAt()).payoutMethodSnapshot(c.getPayoutMethodSnapshot()).payoutRegistrationNumber(c.getPayoutRegistrationNumber())
                .checkInPin(null)
                .checkInQrToken(null)
                .arrivalInstructions(s != null && ShiftRules.reservesSlot(c) ? s.getDetails() : null)
                .attendanceRecorded(c.getCheckInSelfie() != null)
                .checkInMethod(c.getCheckInMethod())
                .disputeReason(c.getDisputeReason())
                .disputedAt(c.getDisputedAt())
                .hospitalRating(c.getHospitalRating())
                .hospitalReview(c.getHospitalReview())
                .ratingTags(c.getRatingTags())
                .ratedAt(c.getRatedAt())
                .claimedAt(c.getClaimedAt())
                .checkedInAt(c.getCheckedInAt())
                .completedAt(c.getCompletedAt())
                .candidateName(c.getPayoutCandidateName() != null ? c.getPayoutCandidateName() : cand != null ? cand.getName() : "Name not recorded")
                .candidateRole(cand != null ? cand.getProfession() : "Healthcare Professional")
                .candidateSpecialty(cand != null ? cand.getSpecialty() : null)
                .candidateCity(cand != null ? cand.getCity() : null)
                .candidateQualification(cand != null ? cand.getQualification() : null)
                .candidateExperience(cand != null ? cand.getExperienceYears() : null)
                .councilVerified(cand != null && "VERIFIED".equalsIgnoreCase(cand.getKycStatus()))
                .candidateRatingAvg(cand != null && cand.getShiftRatingAvg() != null ? cand.getShiftRatingAvg().doubleValue() : 0.0)
                .candidateRatingCount(cand != null && cand.getShiftRatingCount() != null ? cand.getShiftRatingCount() : 0)
                .completedShiftsCount(cand != null && cand.getCompletedShiftsCount() != null ? cand.getCompletedShiftsCount() : 0)
                .matchScore(cand != null && s != null ? ClinicalMatchingEngine.calculateShiftMatch(cand, false, s, f) : 50)
                .build();
    }

    public static int calculateShiftMatchScore(CandidateProfile candidate, LocumShift shift) {
        return ClinicalMatchingEngine.calculateShiftMatch(candidate, false, shift, null);
    }
}
