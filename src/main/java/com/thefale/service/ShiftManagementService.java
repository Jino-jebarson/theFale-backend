package com.thefale.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftManagementService {

    private final FacilityProfileRepository facilityProfileRepository;
    private final FacilityService facilityService;
    private final FacilityWalletRepository facilityWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final LocumShiftRepository locumShiftRepository;
    private final ShiftClaimRepository shiftClaimRepository;
    private final MinimumRoleRateRepository minimumRoleRateRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public LocumShiftDto postShift(CustomUserPrincipal principal, PostShiftRequest request) {
        FacilityProfile facility = facilityService.getOrCreateFacilityProfile(principal);

        if (!request.getEndAt().isAfter(request.getStartAt())) {
            throw new BadRequestException("Shift end time must be after start time");
        }

        // Server derives duration hours with fallback
        long hours = Duration.between(request.getStartAt(), request.getEndAt()).toHours();
        if (hours <= 0 && request.getHours() != null && request.getHours() > 0) {
            hours = request.getHours();
        }
        if (hours < 4 || hours > 24) {
            throw new BadRequestException("Shift duration must be between 4 and 24 hours. Given duration: " + hours + " hours");
        }

        BigDecimal baseRateRupees = BigDecimal.valueOf(request.getRatePerHourPaise())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal surgeRateRupees = BigDecimal.valueOf(request.getSurgeRatePerHourPaise() != null ? request.getSurgeRatePerHourPaise() : 0L)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // Statutory wage floor check
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

        // Escrow Calculation: total required = (base + surge) * hours * openings
        BigDecimal totalPerWorker = baseRateRupees.add(surgeRateRupees).multiply(BigDecimal.valueOf(hours));
        BigDecimal totalEscrowRequired = totalPerWorker.multiply(BigDecimal.valueOf(request.getOpenings()));

        // Pessimistic Escrow Lock on Facility Wallet
        FacilityWallet wallet = facilityWalletRepository.findByFacilityIdWithLock(facility.getId())
                .orElseThrow(() -> new BadRequestException("Facility wallet not initialized. Please onboard and top up your escrow reserve."));

        if (wallet.getAvailableBalance().compareTo(totalEscrowRequired) < 0) {
            throw new BadRequestException("Insufficient escrow reserve. Required: ₹" + totalEscrowRequired +
                    ", Available: ₹" + wallet.getAvailableBalance() + ". Please top up your wallet escrow reserve before broadcasting shifts.");
        }

        wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(totalEscrowRequired));
        wallet.setLockedEscrowBalance(wallet.getLockedEscrowBalance().add(totalEscrowRequired));
        facilityWalletRepository.save(wallet);

        String escrowRef = "ESCROW-HOLD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        WalletTransaction tx = WalletTransaction.builder()
                .walletId(wallet.getId())
                .type("escrow_hold")
                .amount(totalEscrowRequired.negate())
                .balanceAfter(wallet.getAvailableBalance())
                .status("In Escrow")
                .refNumber(escrowRef)
                .title("Escrow Lock: " + request.getTitle())
                .subtitle(request.getOpenings() + " slot(s) · " + hours + " hrs @ ₹" + (baseRateRupees.add(surgeRateRupees)) + "/hr")
                .build();
        walletTransactionRepository.save(tx);

        LocumShift shift = LocumShift.builder()
                .facilityId(facility.getId())
                .title(request.getTitle().trim())
                .department(request.getDepartment().trim())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .hours((int) hours)
                .ratePerHour(baseRateRupees)
                .surgeRatePerHour(surgeRateRupees)
                .surgeLabel(request.getSurgeLabel())
                .openings(request.getOpenings())
                .slotsClaimed(0)
                .urgent(request.isUrgent())
                .status("PENDING_APPROVAL")
                .build();

        LocumShift saved = locumShiftRepository.save(shift);
        auditLogService.logEvent(principal.getId(), "SHIFT_POSTED", "LocumShift", saved.getId().toString(), null);

        return mapToDto(saved, facility, false);
    }

    @Transactional(readOnly = true)
    public List<LocumShiftDto> getOpenShifts(CustomUserPrincipal principal) {
        UUID candidateId = null;
        if (principal != null && "ROLE_CANDIDATE".equalsIgnoreCase(principal.getRole())) {
            candidateId = candidateProfileRepository.findByUserId(principal.getId())
                    .map(CandidateProfile::getId)
                    .orElse(null);
        }

        final UUID finalCandId = candidateId;
        return locumShiftRepository.findOpenShifts().stream()
                .map(s -> {
                    FacilityProfile facility = facilityProfileRepository.findById(s.getFacilityId()).orElse(null);
                    boolean hasClaimed = finalCandId != null && shiftClaimRepository.existsByShiftIdAndCandidateId(s.getId(), finalCandId);
                    return mapToDto(s, facility, hasClaimed);
                })
                .collect(Collectors.toList());
    }

    @Transactional
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

        if (shift.getSlotsClaimed() > 0) {
            throw new BadRequestException("Cannot cancel shift with active claimed clinician slots. Please resolve or dispute claimed slots.");
        }

        shift.setStatus("CANCELLED");
        locumShiftRepository.save(shift);

        // Refund locked escrow
        BigDecimal totalPerWorker = shift.getRatePerHour().add(shift.getSurgeRatePerHour()).multiply(BigDecimal.valueOf(shift.getHours()));
        BigDecimal refundAmount = totalPerWorker.multiply(BigDecimal.valueOf(shift.getOpenings()));

        FacilityWallet wallet = facilityWalletRepository.findByFacilityIdWithLock(facility.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Facility wallet not found"));

        wallet.setLockedEscrowBalance(wallet.getLockedEscrowBalance().subtract(refundAmount));
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
                .subtitle("Cancelled shift broadcast refund")
                .build();
        walletTransactionRepository.save(tx);

        auditLogService.logEvent(principal.getId(), "SHIFT_CANCELLED", "LocumShift", shift.getId().toString(), null);
    }

    public LocumShiftDto mapToDto(LocumShift s, FacilityProfile facility, boolean hasClaimed) {
        long ratePaise = s.getRatePerHour().multiply(BigDecimal.valueOf(100)).longValue();
        long surgePaise = s.getSurgeRatePerHour().multiply(BigDecimal.valueOf(100)).longValue();
        long totalPayPaise = (ratePaise + surgePaise) * s.getHours();

        return LocumShiftDto.builder()
                .id(s.getId())
                .facilityId(s.getFacilityId())
                .facilityName(facility != null ? facility.getHospitalName() : "Hospital")
                .city(facility != null ? facility.getCity() : "City")
                .title(s.getTitle())
                .department(s.getDepartment())
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
                .hasClaimed(hasClaimed)
                .build();
    }
}
