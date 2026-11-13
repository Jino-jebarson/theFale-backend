package com.thefale.service;

import com.thefale.dto.*;
import com.thefale.entity.*;
import com.thefale.exception.BadRequestException;
import com.thefale.exception.ConflictException;
import com.thefale.exception.ForbiddenException;
import com.thefale.repository.*;
import com.thefale.security.CustomUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShiftFlowServiceTest {

    @Mock private FacilityProfileRepository facilityProfileRepository;
    @Mock private FacilityService facilityService;
    @Mock private FacilityWalletRepository facilityWalletRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private LocumShiftRepository locumShiftRepository;
    @Mock private ShiftClaimRepository shiftClaimRepository;
    @Mock private MinimumRoleRateRepository minimumRoleRateRepository;
    @Mock private CandidateProfileRepository candidateProfileRepository;
    @Mock private UserRepository userRepository;
    @Mock private HospitalBankAccountRepository hospitalBankAccountRepository;
    @Mock private PlatformSettingRepository platformSettingRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private AuditLogService auditLogService;

    // Additional mocks for AdminService
    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private StudentUpgradeRequestRepository studentUpgradeRequestRepository;
    @Mock private CandidateLevelHistoryRepository candidateLevelHistoryRepository;
    @Mock private HuddlePostRepository huddlePostRepository;
    @Mock private HuddleFlagRepository huddleFlagRepository;
    @Mock private AuditEventRepository auditEventRepository;
    @Mock private JobApplicationRepository jobApplicationRepository;
    @Mock private CandidateDocumentRepository candidateDocumentRepository;
    @Mock private CandidatePayoutMethodRepository candidatePayoutMethodRepository;
    @Mock private FacilityDocumentRepository facilityDocumentRepository;
    @Mock private HuddleService huddleService;
    @Mock private MentorApplicationRepository mentorApplicationRepository;
    @Mock private UnlockedCandidateRepository unlockedCandidateRepository;

    private ShiftManagementService shiftManagementService;
    private ShiftClaimService shiftClaimService;
    private AdminService adminService;
    private FacilityJobManagementService facilityJobManagementService;

    private UUID facilityUserId;
    private UUID facilityId;
    private UUID candidateUserId;
    private UUID candidateId;
    private CustomUserPrincipal facilityPrincipal;
    private CustomUserPrincipal candidatePrincipal;
    private CustomUserPrincipal studentPrincipal;
    private CustomUserPrincipal adminPrincipal;
    private FacilityProfile verifiedFacility;
    private FacilityWallet facilityWallet;

    @BeforeEach
    void setUp() {
        shiftManagementService = new ShiftManagementService(
                facilityProfileRepository,
                facilityService,
                facilityWalletRepository,
                walletTransactionRepository,
                locumShiftRepository,
                shiftClaimRepository,
                minimumRoleRateRepository,
                candidateProfileRepository,
                userRepository,
                hospitalBankAccountRepository,
                platformSettingRepository,
                notificationRepository,
                auditLogService
        );

        shiftClaimService = new ShiftClaimService(
                locumShiftRepository,
                shiftClaimRepository,
                candidateProfileRepository,
                facilityProfileRepository,
                facilityWalletRepository,
                walletTransactionRepository,
                platformSettingRepository,
                notificationRepository,
                userRepository,
                auditLogService,
                candidateDocumentRepository
        );

        adminService = new AdminService(
                candidateProfileRepository,
                facilityProfileRepository,
                jobPostingRepository,
                shiftClaimRepository,
                locumShiftRepository,
                facilityWalletRepository,
                walletTransactionRepository,
                studentUpgradeRequestRepository,
                candidateLevelHistoryRepository,
                huddlePostRepository,
                huddleFlagRepository,
                userRepository,
                notificationRepository,
                auditEventRepository,
                auditLogService,
                jobApplicationRepository,
                candidateDocumentRepository,
                facilityDocumentRepository,
                huddleService,
                mentorApplicationRepository,
                shiftManagementService,
                platformSettingRepository,
                candidatePayoutMethodRepository
        );

        facilityJobManagementService = new FacilityJobManagementService(
                facilityProfileRepository,
                facilityService,
                jobPostingRepository,
                jobApplicationRepository,
                locumShiftRepository,
                shiftClaimRepository,
                candidateProfileRepository,
                unlockedCandidateRepository,
                userRepository,
                candidateDocumentRepository,
                notificationRepository,
                auditLogService,
                shiftClaimService
        );

        facilityUserId = UUID.randomUUID();
        facilityId = UUID.randomUUID();
        candidateUserId = UUID.randomUUID();
        candidateId = UUID.randomUUID();

        facilityPrincipal = CustomUserPrincipal.builder()
                .id(facilityUserId)
                .role("ROLE_FACILITY")
                .enabled(true)
                .build();

        candidatePrincipal = CustomUserPrincipal.builder()
                .id(candidateUserId)
                .role("ROLE_CANDIDATE")
                .candidateLevel("PROFESSIONAL")
                .enabled(true)
                .build();

        studentPrincipal = CustomUserPrincipal.builder()
                .id(candidateUserId)
                .role("ROLE_CANDIDATE")
                .candidateLevel("STUDENT")
                .enabled(true)
                .build();

        adminPrincipal = CustomUserPrincipal.builder()
                .id(UUID.randomUUID())
                .role("ROLE_ADMIN")
                .enabled(true)
                .build();

        verifiedFacility = FacilityProfile.builder()
                .id(facilityId)
                .userId(facilityUserId)
                .hospitalName("Apollo Critical Care")
                .city("Bengaluru")
                .kycStatus("VERIFIED")
                .build();

        when(candidatePayoutMethodRepository.findByCandidateIdWithLock(any())).thenReturn(Optional.of(CandidatePayoutMethod.builder().methodType("bank").bankName("QA Bank").accountNumberLast4("0001").status("Verified").verifiedAt(Instant.now()).build()));
        facilityWallet = FacilityWallet.builder()
                .id(UUID.randomUUID())
                .facilityId(facilityId)
                .availableBalance(new BigDecimal("100000.00"))
                .lockedEscrowBalance(BigDecimal.ZERO)
                .build();

        when(facilityService.getOrCreateFacilityProfile(facilityPrincipal)).thenReturn(verifiedFacility);
        when(hospitalBankAccountRepository.findByFacilityId(facilityId)).thenReturn(Optional.of(
                HospitalBankAccount.builder().facilityId(facilityId).status("Verified").build()
        ));
        when(facilityWalletRepository.findByFacilityIdWithLock(facilityId)).thenReturn(Optional.of(facilityWallet));
        when(facilityWalletRepository.findByFacilityId(facilityId)).thenReturn(Optional.of(facilityWallet));
        when(minimumRoleRateRepository.findAll()).thenReturn(Collections.emptyList());
        when(locumShiftRepository.save(any(LocumShift.class))).thenAnswer(inv -> {
            LocumShift s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });
        when(shiftClaimRepository.save(any(ShiftClaim.class))).thenAnswer(inv -> {
            ShiftClaim c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });
    }

    @Test
    void firstShiftNeedsApprovalAndBothPostsReserveEightPercent() {
        PostShiftRequest req = PostShiftRequest.builder().details(testDetails())
                .title("ICU Staff Nurse Night Duty")
                .department("Critical Care")
                .startAt(Instant.now().plus(2, ChronoUnit.DAYS))
                .endAt(Instant.now().plus(2, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS))
                .ratePerHourPaise(50000L) // Rs 500/hr * 8h = Rs 4000 base
                .openings(1)
                .urgent(false)
                .build();

        // 1st valid shift -> PENDING_APPROVAL + 0% fee
        when(locumShiftRepository.countValidFeeShiftsByFacility(facilityId)).thenReturn(0L);
        when(locumShiftRepository.countApprovedShiftsByFacility(facilityId)).thenReturn(0L);

        LocumShiftDto first = shiftManagementService.postShift(facilityPrincipal, req);

        assertThat(first.getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(first.isAutoApproved()).isFalse();
        assertThat(first.getFacilityFeePercent()).isEqualTo(8.0);
        assertThat(first.getFacilityFeePaise()).isEqualTo(32000L);
        assertThat(first.getPlatformFeeGstPaise()).isEqualTo(5760L);
        assertThat(first.getTotalEscrowRequiredPaise()).isEqualTo(437760L);
        assertThat(facilityWallet.getLockedEscrowBalance()).isEqualByComparingTo("4377.60");

        // 2nd shift -> OPEN auto-live + 5% facility fee (Rs 200 on Rs 4000)
        when(locumShiftRepository.countValidFeeShiftsByFacility(facilityId)).thenReturn(1L);
        when(locumShiftRepository.countApprovedShiftsByFacility(facilityId)).thenReturn(1L);
        when(candidateProfileRepository.findAll()).thenReturn(Collections.emptyList());

        LocumShiftDto second = shiftManagementService.postShift(facilityPrincipal, req);

        assertThat(second.getStatus()).isEqualTo("OPEN");
        assertThat(second.isAutoApproved()).isTrue();
        assertThat(second.getFacilityFeePercent()).isEqualTo(8.0);
        assertThat(second.getFacilityFeePaise()).isEqualTo(32000L);
        assertThat(facilityWallet.getLockedEscrowBalance()).isEqualByComparingTo("8755.20");
    }

    @Test
    void updateShiftReconcilesEscrowDelta_andBlocksRateDropAfterClaim() {
        UUID shiftId = UUID.randomUUID();
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant end = start.plus(8, ChronoUnit.HOURS);

        LocumShift existing = LocumShift.builder()
                .id(shiftId)
                .facilityId(facilityId)
                .title("ICU Shift")
                .department("Critical Care")
                .startAt(start)
                .endAt(end)
                .hours(8)
                .ratePerHour(new BigDecimal("500.00"))
                .surgeRatePerHour(BigDecimal.ZERO)
                .openings(1)
                .slotsClaimed(0)
                .status("OPEN")
                .facilityFeePercent(new BigDecimal("5.00"))
                .facilityFeeAmount(new BigDecimal("200.00"))
                .build();

        facilityWallet.setLockedEscrowBalance(new BigDecimal("4200.00"));
        when(locumShiftRepository.findByIdWithLock(shiftId)).thenReturn(Optional.of(existing));
        when(shiftClaimRepository.findByShiftId(shiftId)).thenReturn(Collections.emptyList());

        PostShiftRequest increaseOpenings = PostShiftRequest.builder().details(testDetails())
                .title("ICU Shift Updated")
                .department("Critical Care")
                .startAt(start)
                .endAt(end)
                .ratePerHourPaise(50000L)
                .openings(2)
                .build();

        LocumShiftDto updated = shiftManagementService.updateShift(facilityPrincipal, shiftId, increaseOpenings);
        assertThat(updated.getOpenings()).isEqualTo(2);
        // Doubled escrow + 5% fee = 8400 total
        assertThat(facilityWallet.getLockedEscrowBalance()).isEqualByComparingTo("8400.00");

        // Now add an active claim -> lowering hourly rate must be blocked
        when(shiftClaimRepository.findByShiftId(shiftId)).thenReturn(List.of(
                ShiftClaim.builder().id(UUID.randomUUID()).shiftId(shiftId).status("CLAIMED").build()
        ));
        PostShiftRequest dropRate = PostShiftRequest.builder().details(testDetails())
                .title("ICU Shift Updated")
                .department("Critical Care")
                .startAt(start)
                .endAt(end)
                .ratePerHourPaise(40000L)
                .openings(2)
                .build();

        assertThatThrownBy(() -> shiftManagementService.updateShift(facilityPrincipal, shiftId, dropRate))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot reduce hourly rate");
    }

    @Test
    void updatePersistsRequirementsAndArrivalDetailsWithoutReservingPendingApplications() {
        LocumShift shift = candidateShift();
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        shift.setStartAt(start); shift.setEndAt(start.plus(8, ChronoUnit.HOURS));
        ShiftDetails details = testDetails(); details.setProfession("Doctor"); details.setReportTo("Jino");
        details.setReportLocation("ICU"); details.setDressRequirement("Own scrubs");
        details.setItemsToBring("Council ID card"); details.setReportingNotes("Staff entrance");
        PostShiftRequest request = PostShiftRequest.builder().title(shift.getTitle()).department(shift.getDepartment())
                .startAt(shift.getStartAt()).endAt(shift.getEndAt()).ratePerHourPaise(100000L).openings(1).details(details).build();
        when(shiftClaimRepository.findByShiftId(shift.getId())).thenReturn(List.of(
                ShiftClaim.builder().status("CLAIMED").build()));

        LocumShiftDto updated = shiftManagementService.updateShift(facilityPrincipal, shift.getId(), request);

        assertThat(shift.getDetails()).isEqualTo(details);
        assertThat(updated.getDetails().getProfession()).isEqualTo("Doctor");
        assertThat(updated.getDetails().getReportTo()).isEqualTo("Jino");
        assertThat(updated.getDetails().getReportingNotes()).isEqualTo("Staff entrance");
        assertThat(updated.getSlotsClaimed()).isZero();
        assertThat(updated.getStatus()).isEqualTo("OPEN");
        assertThat(shiftManagementService.mapToDto(shift, verifiedFacility, false).getDetails().getReportTo()).isNull();

        when(shiftClaimRepository.findByShiftId(shift.getId())).thenReturn(List.of(
                ShiftClaim.builder().status("CONFIRMED").build()));
        request.setDetails(testDetails());
        assertThatThrownBy(() -> shiftManagementService.updateShift(facilityPrincipal, shift.getId(), request))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Cannot change role");
        assertThat(shift.getDetails().getProfession()).isEqualTo("Doctor");
        request.setDetails(null);
        assertThatThrownBy(() -> shiftManagementService.updateShift(facilityPrincipal, shift.getId(), request))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("required");
    }

    @Test
    void cancelShiftPreventsDoubleRefund() {
        UUID shiftId = UUID.randomUUID();
        LocumShift shift = LocumShift.builder()
                .id(shiftId)
                .facilityId(facilityId)
                .title("Emergency ER Cover")
                .hours(8)
                .ratePerHour(new BigDecimal("600.00"))
                .surgeRatePerHour(BigDecimal.ZERO)
                .openings(1)
                .status("OPEN")
                .facilityFeeAmount(BigDecimal.ZERO)
                .build();
        facilityWallet.setLockedEscrowBalance(new BigDecimal("4800.00"));
        BigDecimal initialAvailable = facilityWallet.getAvailableBalance();

        when(locumShiftRepository.findByIdWithLock(shiftId)).thenReturn(Optional.of(shift));
        when(shiftClaimRepository.findByShiftId(shiftId)).thenReturn(Collections.emptyList());

        shiftManagementService.cancelShift(facilityPrincipal, shiftId);

        assertThat(shift.getStatus()).isEqualTo("CANCELLED");
        assertThat(facilityWallet.getAvailableBalance()).isEqualByComparingTo(initialAvailable.add(new BigDecimal("4800.00")));
        assertThat(facilityWallet.getLockedEscrowBalance()).isEqualByComparingTo("0.00");

        // Second cancel attempt throws and never refunds twice
        assertThatThrownBy(() -> shiftManagementService.cancelShift(facilityPrincipal, shiftId))
                .isInstanceOf(BadRequestException.class);
        assertThat(facilityWallet.getAvailableBalance()).isEqualByComparingTo(initialAvailable.add(new BigDecimal("4800.00")));
    }

    @Test
    void claimShiftBlocksStudentsAndUnverifiedKycAndOverlappingShifts() {
        UUID shiftId = UUID.randomUUID();

        // 1. Students strictly blocked
        when(userRepository.findById(candidateUserId)).thenReturn(Optional.of(
                User.builder().id(candidateUserId).candidateLevel("STUDENT").build()
        ));
        assertThatThrownBy(() -> shiftClaimService.claimShift(studentPrincipal, shiftId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("students are not permitted");

        // 2. Non-VERIFIED candidate blocked
        CandidateProfile pendingCandidate = CandidateProfile.builder()
                .id(candidateId)
                .userId(candidateUserId)
                .kycStatus("PENDING")
                .build();
        when(userRepository.findById(candidateUserId)).thenReturn(Optional.of(
                User.builder().id(candidateUserId).candidateLevel("PROFESSIONAL").build()
        ));
        when(candidateProfileRepository.findByUserId(candidateUserId)).thenReturn(Optional.of(pendingCandidate));

        assertThatThrownBy(() -> shiftClaimService.claimShift(candidatePrincipal, shiftId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("VERIFIED");

        // 3. Verified candidate blocked when active shift overlaps
        CandidateProfile verifiedCandidate = CandidateProfile.builder()
                .id(candidateId)
                .userId(candidateUserId)
                .kycStatus("VERIFIED")
                .build();
        when(candidateProfileRepository.findByUserId(candidateUserId)).thenReturn(Optional.of(verifiedCandidate));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(8, ChronoUnit.HOURS);
        LocumShift targetShift = LocumShift.builder()
                .id(shiftId)
                .facilityId(facilityId)
                .title("OT Nurse")
                .department("Surgery")
                .startAt(start)
                .endAt(end)
                .hours(8)
                .ratePerHour(new BigDecimal("500.00"))
                .surgeRatePerHour(BigDecimal.ZERO)
                .openings(1)
                .slotsClaimed(0)
                .status("OPEN")
                .build();
        UUID otherShiftId = UUID.randomUUID();
        LocumShift overlappingShift = LocumShift.builder()
                .id(otherShiftId)
                .title("Morning ICU")
                .startAt(start.plus(2, ChronoUnit.HOURS))
                .endAt(end.plus(2, ChronoUnit.HOURS))
                .build();

        when(locumShiftRepository.findByIdWithLock(shiftId)).thenReturn(Optional.of(targetShift));
        when(shiftClaimRepository.findByShiftIdAndCandidateId(shiftId, candidateId)).thenReturn(Optional.empty());
        when(shiftClaimRepository.findActiveClaimsByCandidate(candidateId)).thenReturn(List.of(
                ShiftClaim.builder().id(UUID.randomUUID()).shiftId(otherShiftId).candidateId(candidateId).status("CONFIRMED").build()
        ));
        when(locumShiftRepository.findById(otherShiftId)).thenReturn(Optional.of(overlappingShift));

        assertThatThrownBy(() -> shiftClaimService.claimShift(candidatePrincipal, shiftId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Schedule conflict");
    }

    @Test
    void cliniciansPayNoPlatformFeeOnAnyShift() {
        CandidateProfile candidate = CandidateProfile.builder()
                .id(candidateId)
                .userId(candidateUserId)
                .name("Dr. Priya")
                .kycStatus("VERIFIED")
                .build();
        when(userRepository.findById(candidateUserId)).thenReturn(Optional.of(
                User.builder().id(candidateUserId).candidateLevel("PROFESSIONAL").build()
        ));
        when(candidateProfileRepository.findByUserId(candidateUserId)).thenReturn(Optional.of(candidate));
        when(shiftClaimRepository.findActiveClaimsByCandidate(candidateId)).thenReturn(Collections.emptyList());
        when(facilityProfileRepository.findById(facilityId)).thenReturn(Optional.of(verifiedFacility));

        UUID s1 = UUID.randomUUID();
        LocumShift shift1 = LocumShift.builder()
                .id(s1).facilityId(facilityId).title("Ward Duty 1").department("Medicine")
                .startAt(Instant.now().plus(2, ChronoUnit.DAYS))
                .endAt(Instant.now().plus(2, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS))
                .hours(8).ratePerHour(new BigDecimal("1000.00")).surgeRatePerHour(BigDecimal.ZERO)
                .openings(2).slotsClaimed(0).status("OPEN")
                .build();
        when(locumShiftRepository.findByIdWithLock(s1)).thenReturn(Optional.of(shift1));
        when(shiftClaimRepository.findByShiftIdAndCandidateId(s1, candidateId)).thenReturn(Optional.empty());
        when(shiftClaimRepository.countValidFeeClaimsByCandidate(candidateId)).thenReturn(0L);

        ShiftClaimDto firstClaim = shiftClaimService.claimShift(candidatePrincipal, s1);
        assertThat(firstClaim.getPlatformFeePercent()).isEqualTo(0.0);
        assertThat(firstClaim.getPlatformFeePaise()).isEqualTo(0L);
        // Gross = Rs 8000, TDS = 1% = Rs 80, Fee = 0 -> Net = Rs 7920
        assertThat(firstClaim.getNetAmountPaise()).isEqualTo(792000L);

        // 2nd valid claim -> 5% platform fee
        UUID s2 = UUID.randomUUID();
        LocumShift shift2 = LocumShift.builder()
                .id(s2).facilityId(facilityId).title("Ward Duty 2").department("Medicine")
                .startAt(Instant.now().plus(4, ChronoUnit.DAYS))
                .endAt(Instant.now().plus(4, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS))
                .hours(8).ratePerHour(new BigDecimal("1000.00")).surgeRatePerHour(BigDecimal.ZERO)
                .openings(2).slotsClaimed(0).status("OPEN")
                .build();
        when(locumShiftRepository.findByIdWithLock(s2)).thenReturn(Optional.of(shift2));
        when(shiftClaimRepository.findByShiftIdAndCandidateId(s2, candidateId)).thenReturn(Optional.empty());
        when(shiftClaimRepository.countValidFeeClaimsByCandidate(candidateId)).thenReturn(1L);

        ShiftClaimDto secondClaim = shiftClaimService.claimShift(candidatePrincipal, s2);
        assertThat(secondClaim.getPlatformFeePercent()).isEqualTo(0.0);
        assertThat(secondClaim.getPlatformFeePaise()).isEqualTo(0L);
        // Gross = 8000, 5% fee = 400, 1% TDS = 80 -> Net = 7520
        assertThat(secondClaim.getNetAmountPaise()).isEqualTo(792000L);
    }

    @Test
    void checkInValidatesPin_andRateShiftClaimBoostsProfileAndMatchingEngine() {
        CandidateProfile candidate = CandidateProfile.builder()
                .id(candidateId)
                .userId(candidateUserId)
                .name("Nurse Kavya")
                .profession("Staff Nurse")
                .specialty("Critical Care")
                .qualification("B.Sc Nursing")
                .experienceYears("3 Years")
                .city("Bengaluru")
                .kycStatus("VERIFIED")
                .build();
        UUID shiftId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();

        LocumShift shift = LocumShift.builder()
                .id(shiftId)
                .facilityId(facilityId)
                .title("ICU Night Duty")
                .department("Critical Care")
                .startAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .endAt(Instant.now().plus(8, ChronoUnit.HOURS))
                .hours(8)
                .ratePerHour(new BigDecimal("500.00"))
                .surgeRatePerHour(BigDecimal.ZERO)
                .build();

        ShiftClaim claim = ShiftClaim.builder()
                .id(claimId)
                .shiftId(shiftId)
                .candidateId(candidateId)
                .status("CONFIRMED")
                .checkInPin("482901")
                .grossAmount(new BigDecimal("4000.00"))
                .platformFee(BigDecimal.ZERO)
                .tdsAmount(new BigDecimal("40.00"))
                .netAmount(new BigDecimal("3960.00"))
                .build();

        when(candidateProfileRepository.findByUserId(candidateUserId)).thenReturn(Optional.of(candidate));
        when(shiftClaimRepository.findByIdWithLock(claimId)).thenReturn(Optional.of(claim));
        when(locumShiftRepository.findById(shiftId)).thenReturn(Optional.of(shift));
        when(facilityProfileRepository.findById(facilityId)).thenReturn(Optional.of(verifiedFacility));
        when(facilityProfileRepository.findByUserId(facilityUserId)).thenReturn(Optional.of(verifiedFacility));
        when(candidateProfileRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        // Wrong PIN rejected
        assertThatThrownBy(() -> shiftClaimService.checkIn(candidatePrincipal, claimId, attendanceRequest("0000")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid six-digit");

        // Correct PIN transitions to CHECKED_IN
        ShiftClaimDto checkedIn = shiftClaimService.checkIn(candidatePrincipal, claimId, attendanceRequest("482901"));
        assertThat(checkedIn.getStatus()).isEqualTo("CHECKED_IN");

        // Compare baseline matching score before rating
        int baselineScore = ClinicalMatchingEngine.calculateShiftMatch(candidate, false, shift, verifiedFacility);

        // Complete claim & submit 5-star hospital rating
        claim.setStatus("COMPLETED");
        when(shiftClaimRepository.findRatedClaimsByCandidate(candidateId)).thenReturn(List.of(claim));

        ShiftClaimDto rated = shiftClaimService.rateShiftClaim(
                facilityPrincipal,
                claimId,
                RateShiftClaimRequest.builder()
                        .rating(5)
                        .review("Excellent ICU handover and patient care.")
                        .tags(List.of("Punctual", "Clinical Excellence"))
                        .build()
        );

        assertThat(rated.getHospitalRating()).isEqualTo(5);
        assertThat(candidate.getShiftRatingAvg()).isEqualByComparingTo("5.00");
        assertThat(candidate.getShiftRatingCount()).isEqualTo(1);

        int boostedScore = ClinicalMatchingEngine.calculateShiftMatch(candidate, false, shift, verifiedFacility);
        assertThat(boostedScore).isGreaterThanOrEqualTo(baselineScore);
    }

    @Test
    void adminResolveDisputePartialSplitAdjustsPayoutAndRefundsHospital() {
        UUID shiftId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();

        LocumShift shift = LocumShift.builder()
                .id(shiftId)
                .facilityId(facilityId)
                .title("Emergency Shift")
                .hours(8)
                .ratePerHour(new BigDecimal("1000.00"))
                .surgeRatePerHour(BigDecimal.ZERO)
                .openings(1)
                .slotsClaimed(1)
                .facilityFeePercent(new BigDecimal("5.00"))
                .build();

        // Scheduled 8h * 1000 = 8000 gross; candidate 5% fee = 400; facility 5% fee = 400 -> 8400 locked
        ShiftClaim claim = ShiftClaim.builder()
                .id(claimId)
                .shiftId(shiftId)
                .candidateId(candidateId)
                .status("DISPUTED")
                .actualHours(8)
                .grossAmount(new BigDecimal("8000.00"))
                .platformFee(new BigDecimal("400.00"))
                .platformFeePercent(new BigDecimal("5.00"))
                .tdsAmount(new BigDecimal("80.00"))
                .netAmount(new BigDecimal("7520.00"))
                .payoutStatus("IN_ESCROW")
                .disputeReason("Left ward 2h early per supervisor log")
                .build();

        facilityWallet.setLockedEscrowBalance(new BigDecimal("8400.00"));
        BigDecimal availBefore = facilityWallet.getAvailableBalance();

        when(shiftClaimRepository.findByIdWithLock(claimId)).thenReturn(Optional.of(claim));
        when(shiftClaimRepository.countCompletedClaimsExcluding(candidateId, claimId)).thenReturn(1L);
        when(locumShiftRepository.findByIdWithLock(shiftId)).thenReturn(Optional.of(shift));
        when(candidateProfileRepository.findById(candidateId)).thenReturn(Optional.of(
                CandidateProfile.builder().id(candidateId).userId(candidateUserId).completedShiftsCount(0).build()
        ));

        adminService.resolveShiftDispute(
                adminPrincipal,
                claimId,
                ResolveDisputeRequest.builder()
                        .resolution("PARTIAL_SPLIT")
                        .approvedHours(6)
                        .notes("Verified 6h duty on biometric ward log.")
                        .build()
        );

        assertThat(claim.getStatus()).isEqualTo("COMPLETED");
        assertThat(claim.getPayoutStatus()).isEqualTo("PENDING_DISBURSEMENT");
        assertThat(claim.getActualHours()).isEqualTo(6);
        // 6h * 1000 = 6000 gross; 5% fee = 300; 1% TDS = 60 -> net = 5640
        assertThat(claim.getGrossAmount()).isEqualByComparingTo("6000.00");
        assertThat(claim.getPlatformFee()).isEqualByComparingTo("0.00");
        assertThat(claim.getNetAmount()).isEqualByComparingTo("5940.00");
        // Hospital refund for 2 unused hrs: 2000 pay + 100 facility fee = 2100
        assertThat(facilityWallet.getAvailableBalance()).isEqualByComparingTo(availBefore.add(new BigDecimal("2400.00")));
        assertThat(facilityWallet.getLockedEscrowBalance()).isEqualByComparingTo("6000.00");
    }

    @Test
    void getFacilityApplicants_withOnlyShiftsAndNoJobs_skipsEmptyJobInQueryAndReturnsClaimedShift() {
        UUID shiftId = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();

        LocumShift shift = LocumShift.builder()
                .id(shiftId)
                .facilityId(facilityId)
                .title("Night ICU Nurse")
                .department("ICU")
                .status("FILLED")
                .hours(8)
                .openings(1)
                .slotsClaimed(1)
                .ratePerHour(new BigDecimal("900.00"))
                .surgeRatePerHour(BigDecimal.ZERO)
                .startAt(Instant.now().plus(2, ChronoUnit.HOURS))
                .endAt(Instant.now().plus(10, ChronoUnit.HOURS))
                .build();

        ShiftClaim claim = ShiftClaim.builder()
                .id(claimId)
                .shiftId(shiftId)
                .candidateId(candidateId)
                .status("CLAIMED")
                .claimedAt(Instant.now())
                .build();

        CandidateProfile candidate = CandidateProfile.builder()
                .id(candidateId)
                .userId(candidateUserId)
                .name("Dr. Priya")
                .profession("ICU Nurse")
                .city("Chennai")
                .kycStatus("VERIFIED")
                .build();

        User candidateUser = User.builder()
                .id(candidateUserId)
                .email("priya@fale.care")
                .phone("+91 98765 43210")
                .candidateLevel("PROFESSIONAL")
                .build();

        when(facilityService.getOrCreateFacilityProfile(facilityPrincipal)).thenReturn(verifiedFacility);
        when(facilityProfileRepository.findById(facilityId)).thenReturn(Optional.of(verifiedFacility));
        when(jobPostingRepository.findByFacilityIdAndDeletedAtIsNull(facilityId)).thenReturn(Collections.emptyList());
        when(locumShiftRepository.findByFacilityIdOrderByStartAtDesc(facilityId)).thenReturn(List.of(shift));
        when(shiftClaimRepository.findByShiftIdIn(List.of(shiftId))).thenReturn(List.of(claim));
        when(candidateProfileRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(userRepository.findById(candidateUserId)).thenReturn(Optional.of(candidateUser));
        when(candidateDocumentRepository.findByCandidateId(candidateId)).thenReturn(Collections.emptyList());

        List<JobApplicationDto> applicants = facilityJobManagementService.getFacilityApplicants(facilityPrincipal, null, "All");

        verify(jobApplicationRepository, never()).findByJobIdIn(anyList());
        assertThat(applicants).hasSize(1);
        assertThat(applicants.get(0).getId()).isEqualTo(claimId);
        assertThat(applicants.get(0).getStatus()).isEqualTo("CLAIMED");
        assertThat(applicants.get(0).getJobTitle()).isEqualTo("Night ICU Nurse (Locum Shift)");
        assertThat(applicants.get(0).getCandidateName()).isEqualTo("Dr. Priya");
    }

    @Test
    void getOpenShifts_includesFilledShiftClaimedByCandidate_withHasClaimedTrue() {
        UUID openShiftId = UUID.randomUUID();
        UUID filledClaimedShiftId = UUID.randomUUID();

        LocumShift openShift = LocumShift.builder()
                .id(openShiftId)
                .facilityId(facilityId)
                .title("Day Ward Nurse")
                .department("General")
                .status("OPEN")
                .hours(8)
                .openings(1)
                .slotsClaimed(0)
                .ratePerHour(new BigDecimal("700.00"))
                .surgeRatePerHour(BigDecimal.ZERO)
                .startAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .endAt(Instant.now().plus(1, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS))
                .build();

        LocumShift filledClaimedShift = LocumShift.builder()
                .id(filledClaimedShiftId)
                .facilityId(facilityId)
                .title("Lab Technician")
                .department("Diagnostics")
                .status("FILLED")
                .hours(12)
                .openings(1)
                .slotsClaimed(1)
                .ratePerHour(new BigDecimal("600.00"))
                .surgeRatePerHour(BigDecimal.ZERO)
                .startAt(Instant.now().plus(2, ChronoUnit.DAYS))
                .endAt(Instant.now().plus(2, ChronoUnit.DAYS).plus(12, ChronoUnit.HOURS))
                .build();

        ShiftClaim activeClaim = ShiftClaim.builder()
                .id(UUID.randomUUID())
                .shiftId(filledClaimedShiftId)
                .candidateId(candidateId)
                .status("CLAIMED")
                .claimedAt(Instant.now())
                .build();

        CandidateProfile candidate = CandidateProfile.builder()
                .id(candidateId)
                .userId(candidateUserId)
                .name("Dr. Priya")
                .kycStatus("VERIFIED")
                .build();

        when(candidateProfileRepository.findByUserId(candidateUserId)).thenReturn(Optional.of(candidate));
        when(facilityProfileRepository.findById(facilityId)).thenReturn(Optional.of(verifiedFacility));
        when(locumShiftRepository.findOpenShifts()).thenReturn(List.of(openShift));
        when(shiftClaimRepository.findByCandidateIdOrderByClaimedAtDesc(candidateId)).thenReturn(List.of(activeClaim));
        when(locumShiftRepository.findAllById(List.of(filledClaimedShiftId))).thenReturn(List.of(filledClaimedShift));

        List<LocumShiftDto> shifts = shiftManagementService.getOpenShifts(candidatePrincipal);

        assertThat(shifts).hasSize(2);
        assertThat(shifts)
                .filteredOn(s -> s.getId().equals(filledClaimedShiftId))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.isHasClaimed()).isTrue();
                    assertThat(s.getStatus()).isEqualTo("FILLED");
                });
        assertThat(shifts)
                .filteredOn(s -> s.getId().equals(openShiftId))
                .singleElement()
                .satisfies(s -> assertThat(s.isHasClaimed()).isFalse());
    }
    private static com.thefale.entity.ShiftDetails testDetails() {
        com.thefale.entity.ShiftDetails d = new com.thefale.entity.ShiftDetails();
        d.setProfession("Nurse"); d.setCity("Bengaluru"); d.setReportTo("Duty supervisor"); d.setReportLocation("Reception");
        return d;
    }
    private static ShiftCheckInRequest attendanceRequest(String pin) {
        ShiftCheckInRequest request = new ShiftCheckInRequest();
        request.setPin(pin); request.setMethod("PIN"); request.setLatitude(12.9); request.setLongitude(77.6); request.setAccuracy(10.0);
        request.setLocationCapturedAt(Instant.now()); request.setSelfieCapturedAt(Instant.now());
        try {
            java.io.ByteArrayOutputStream image = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(160,160,java.awt.image.BufferedImage.TYPE_INT_RGB),"jpeg",image);
            request.setSelfieDataUrl("data:image/jpeg;base64,"+java.util.Base64.getEncoder().encodeToString(image.toByteArray()));
        } catch (Exception e) { throw new IllegalStateException(e); }
        return request;
    }

    private LocumShift candidateShift() {
        CandidateProfile candidate = CandidateProfile.builder().id(candidateId).userId(candidateUserId).name("QA Nurse")
                .profession("Nurse").kycStatus("VERIFIED").experienceYears("3").skills("ICU").build();
        when(candidateProfileRepository.findByUserId(candidateUserId)).thenReturn(Optional.of(candidate));
        when(candidateProfileRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(candidateProfileRepository.findByIdWithLock(candidateId)).thenReturn(Optional.of(candidate));
        when(userRepository.findById(candidateUserId)).thenReturn(Optional.of(User.builder().id(candidateUserId).candidateLevel("PROFESSIONAL").build()));
        when(facilityProfileRepository.findById(facilityId)).thenReturn(Optional.of(verifiedFacility));
        when(facilityProfileRepository.findByUserId(facilityUserId)).thenReturn(Optional.of(verifiedFacility));
        LocumShift shift = LocumShift.builder().id(UUID.randomUUID()).facilityId(facilityId).title("ICU Nurse").department("Critical Care")
                .startAt(Instant.now().minusSeconds(600)).endAt(Instant.now().plusSeconds(28200)).hours(8)
                .ratePerHour(new BigDecimal("1000")).openings(1).facilityFeePercent(new BigDecimal("8"))
                .facilityFeeAmount(new BigDecimal("640")).status("OPEN").details(testDetails()).build();
        when(locumShiftRepository.findByIdWithLock(shift.getId())).thenReturn(Optional.of(shift));
        when(locumShiftRepository.findById(shift.getId())).thenReturn(Optional.of(shift));
        return shift;
    }

    private ShiftClaim application(LocumShift shift) {
        ShiftClaimDto applied = shiftClaimService.claimShift(candidatePrincipal, shift.getId());
        ShiftClaim claim = ShiftClaim.builder().id(applied.getId()).shiftId(shift.getId()).candidateId(candidateId)
                .status("CLAIMED").grossAmount(new BigDecimal("8000")).platformFee(BigDecimal.ZERO)
                .tdsAmount(new BigDecimal("80")).netAmount(new BigDecimal("7920")).build();
        when(shiftClaimRepository.findByIdWithLock(claim.getId())).thenReturn(Optional.of(claim));
        when(shiftClaimRepository.findById(claim.getId())).thenReturn(Optional.of(claim));
        when(shiftClaimRepository.findByShiftId(shift.getId())).thenReturn(List.of(claim));
        return claim;
    }

    @Test
    void applicationsDoNotReserveSlotsAndHospitalConfirmationIssuesPrivateCredentials() {
        LocumShift shift = candidateShift(); ShiftClaim claim = application(shift);
        assertThat(shift.getSlotsClaimed()).isZero(); assertThat(shift.getStatus()).isEqualTo("OPEN");
        assertThat(shiftClaimService.mapClaimToDto(claim, shift, verifiedFacility).getArrivalInstructions()).isNull();
        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest(); request.setStatus("CONFIRMED");
        JobApplicationDto confirmed = facilityJobManagementService.updateApplicationStatus(facilityPrincipal, claim.getId(), request);
        assertThat(shift.getSlotsClaimed()).isEqualTo(1); assertThat(shift.getStatus()).isEqualTo("FILLED");
        assertThat(confirmed.getCheckInPin()).matches("[0-9]{6}"); assertThat(confirmed.getCheckInQrToken()).isNotBlank();
        ShiftClaimDto candidateView = shiftClaimService.mapClaimToDto(claim, shift, verifiedFacility);
        assertThat(candidateView.getCheckInPin()).isNull(); assertThat(candidateView.getCheckInQrToken()).isNull();
        assertThat(candidateView.getArrivalInstructions().getReportTo()).isEqualTo("Duty supervisor");
        assertThatThrownBy(() -> facilityJobManagementService.updateApplicationStatus(facilityPrincipal, claim.getId(), request)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void checkInRequiresEvidenceAndSixDigitPinAndCannotBeReplayed() {
        LocumShift shift = candidateShift(); ShiftClaim claim = application(shift); claim.setStatus("CONFIRMED"); claim.setCheckInPin("123456");
        ShiftCheckInRequest request = attendanceRequest("123456"); request.setSelfieDataUrl(null);
        assertThatThrownBy(() -> shiftClaimService.checkIn(candidatePrincipal, claim.getId(), request)).isInstanceOf(BadRequestException.class);
        assertThat(claim.getStatus()).isEqualTo("CONFIRMED");
        ShiftClaimDto checked = shiftClaimService.checkIn(candidatePrincipal, claim.getId(), attendanceRequest("123456"));
        assertThat(checked.getStatus()).isEqualTo("CHECKED_IN"); assertThat(checked.isAttendanceRecorded()).isTrue();
        assertThat(claim.getCheckInPin()).isNull();
        assertThatThrownBy(() -> shiftClaimService.checkIn(candidatePrincipal, claim.getId(), attendanceRequest("123456"))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void qrCheckInUsesTheBookingTokenAndLimitsIncorrectAttempts() {
        LocumShift shift = candidateShift(); ShiftClaim claim = application(shift); claim.setStatus("CONFIRMED"); claim.setCheckInQrToken("booking-token");
        ShiftCheckInRequest wrong = attendanceRequest(""); wrong.setMethod("QR"); wrong.setQrToken("wrong-booking");
        for (int attempt=0;attempt<5;attempt++) assertThatThrownBy(() -> shiftClaimService.checkIn(candidatePrincipal, claim.getId(), wrong)).isInstanceOf(BadRequestException.class);
        assertThat(claim.getCheckInBlockedUntil()).isAfter(Instant.now());
        ShiftCheckInRequest correct = attendanceRequest(""); correct.setMethod("QR"); correct.setQrToken("booking-token");
        assertThatThrownBy(() -> shiftClaimService.checkIn(candidatePrincipal, claim.getId(), correct)).hasMessageContaining("five minutes");
        claim.setCheckInBlockedUntil(Instant.now().minusSeconds(1));
        assertThat(shiftClaimService.checkIn(candidatePrincipal, claim.getId(), correct).getStatus()).isEqualTo("CHECKED_IN");
        assertThat(claim.getCheckInQrToken()).isNull();
    }

    @Test
    void firstCompletedHospitalShiftRefundsReservedFeeAndNextShiftChargesEightPercent() {
        LocumShift first = candidateShift(); ShiftClaim claim = application(first);
        facilityWallet.setLockedEscrowBalance(new BigDecimal("8640"));
        claim.setStatus("TIMESHEET_SUBMITTED");
        shiftClaimService.completeShift(facilityPrincipal, claim.getId(), new CompleteShiftRequest());
        assertThat(claim.getHospitalFeeAmount()).isEqualByComparingTo("0");
        assertThat(claim.getPlatformFee()).isEqualByComparingTo("0");
        assertThat(facilityWallet.getFirstCompletedShiftId()).isEqualTo(first.getId());
        assertThat(facilityWallet.getAvailableBalance()).isEqualByComparingTo("100640");
        assertThat(facilityWallet.getLockedEscrowBalance()).isEqualByComparingTo("8000");
        LocumShift second = candidateShift(); ShiftClaim secondClaim = application(second); secondClaim.setStatus("TIMESHEET_SUBMITTED");
        facilityWallet.setLockedEscrowBalance(facilityWallet.getLockedEscrowBalance().add(new BigDecimal("8640")));
        shiftClaimService.completeShift(facilityPrincipal, secondClaim.getId(), new CompleteShiftRequest());
        assertThat(secondClaim.getHospitalFeeAmount()).isEqualByComparingTo("640");
        assertThat(secondClaim.getPlatformFee()).isEqualByComparingTo("0");
        assertThat(facilityWallet.getAvailableBalance()).isEqualByComparingTo("100640");
    }

    @Test
    void hospitalCannotCompleteAConfirmedDutyWithoutClinicianTimesheet() {
        LocumShift shift = candidateShift(); ShiftClaim claim = application(shift); claim.setStatus("CONFIRMED");
        assertThatThrownBy(() -> shiftClaimService.completeShift(facilityPrincipal, claim.getId())).isInstanceOf(BadRequestException.class);
        assertThat(facilityWallet.getFirstCompletedShiftId()).isNull();
    }

    @Test
    void requirementsBlockMissingVerifiedCertificatesAndExperience() {
        LocumShift shift = candidateShift(); shift.getDetails().setBlsRequired(true);
        assertThatThrownBy(() -> application(shift)).hasMessageContaining("BLS");
        when(candidateDocumentRepository.findByCandidateId(candidateId)).thenReturn(List.of(CandidateDocument.builder().documentType("BLS").verified(true).build()));
        shift.getDetails().setMinimumExperienceYears(5);
        assertThatThrownBy(() -> application(shift)).hasMessageContaining("5 years");
        shift.getDetails().setMinimumExperienceYears(2);
        assertThat(application(shift).getStatus()).isEqualTo("CLAIMED");
    }
    @Test
    void gstWaiverAndLaterDisbursementUseSavedTaxAmounts() {
        LocumShift first = candidateShift(); first.setPlatformFeeGstPercent(ShiftCharges.GST_PERCENT); first.setPlatformFeeGstAmount(new BigDecimal("115.20"));
        ShiftClaim free = application(first); free.setStatus("TIMESHEET_SUBMITTED");
        facilityWallet.setLockedEscrowBalance(new BigDecimal("8755.20"));
        shiftClaimService.completeShift(facilityPrincipal, free.getId());
        assertThat(free.getHospitalFeeGstAmount()).isEqualByComparingTo("0");
        assertThat(facilityWallet.getAvailableBalance()).isEqualByComparingTo("100755.20");
        assertThat(facilityWallet.getLockedEscrowBalance()).isEqualByComparingTo("8000");
        LocumShift second = candidateShift(); second.setPlatformFeeGstPercent(ShiftCharges.GST_PERCENT); second.setPlatformFeeGstAmount(new BigDecimal("115.20"));
        ShiftClaim paid = application(second); paid.setStatus("TIMESHEET_SUBMITTED");
        facilityWallet.setLockedEscrowBalance(new BigDecimal("16755.20"));
        shiftClaimService.completeShift(facilityPrincipal, paid.getId());
        assertThat(paid.getHospitalFeeAmount()).isEqualByComparingTo("640");
        assertThat(paid.getHospitalFeeGstAmount()).isEqualByComparingTo("115.20");
        assertThat(paid.getNetAmount()).isEqualByComparingTo("7920");
        adminService.disbursePayout(adminPrincipal, paid.getId(), AdminPayoutApprovalRequest.builder().utr("GSTPAY123456").build());
        assertThat(facilityWallet.getLockedEscrowBalance()).isEqualByComparingTo("8000");
        assertThat(facilityWallet.getTotalDisbursed()).isEqualByComparingTo("8755.20");
    }
    @Test
    void gstEditingAndCancellationRefundTheFullReservation() {
        LocumShift shift = candidateShift(); shift.setOpenings(2); shift.setFacilityFeeAmount(new BigDecimal("1280"));
        shift.setPlatformFeeGstPercent(ShiftCharges.GST_PERCENT); shift.setPlatformFeeGstAmount(new BigDecimal("230.40"));
        shift.setStartAt(Instant.now().plus(2, ChronoUnit.DAYS)); shift.setEndAt(shift.getStartAt().plus(8, ChronoUnit.HOURS));
        facilityWallet.setLockedEscrowBalance(new BigDecimal("17510.40"));
        PostShiftRequest request = PostShiftRequest.builder().title(shift.getTitle()).department(shift.getDepartment()).details(testDetails())
                .startAt(shift.getStartAt()).endAt(shift.getEndAt()).ratePerHourPaise(100000L).openings(1).build();
        LocumShiftDto result = shiftManagementService.updateShift(facilityPrincipal, shift.getId(), request);
        assertThat(result.getPlatformFeeGstPaise()).isEqualTo(11520L);
        assertThat(facilityWallet.getLockedEscrowBalance()).isEqualByComparingTo("8755.20");
        shiftManagementService.cancelShift(facilityPrincipal, shift.getId());
        assertThat(facilityWallet.getLockedEscrowBalance()).isEqualByComparingTo("0");
        assertThat(facilityWallet.getAvailableBalance()).isEqualByComparingTo("117510.40");
    }
    @Test
    void gstPartialDisputeRefundAndSnapshotMatchApprovedHours() {
        LocumShift shift = candidateShift(); shift.setPlatformFeeGstPercent(ShiftCharges.GST_PERCENT); shift.setPlatformFeeGstAmount(new BigDecimal("115.20"));
        ShiftClaim claim = application(shift); claim.setStatus("DISPUTED");
        facilityWallet.setFirstCompletedShiftId(UUID.randomUUID()); facilityWallet.setLockedEscrowBalance(new BigDecimal("8755.20"));
        adminService.resolveShiftDispute(adminPrincipal, claim.getId(), ResolveDisputeRequest.builder().resolution("PARTIAL_SPLIT").approvedHours(6).build());
        assertThat(claim.getHospitalFeeAmount()).isEqualByComparingTo("480");
        assertThat(claim.getHospitalFeeGstAmount()).isEqualByComparingTo("86.40");
        assertThat(facilityWallet.getLockedEscrowBalance()).isEqualByComparingTo("6566.40");
        assertThat(facilityWallet.getAvailableBalance()).isEqualByComparingTo("102188.80");
    }
    @Test
    void gstIsRefundedWhenAdminRejectsAFirstPosting() {
        LocumShift shift = candidateShift(); shift.setStatus("PENDING_APPROVAL");
        shift.setPlatformFeeGstPercent(ShiftCharges.GST_PERCENT); shift.setPlatformFeeGstAmount(new BigDecimal("115.20"));
        facilityWallet.setLockedEscrowBalance(new BigDecimal("8755.20"));
        adminService.reviewShift(adminPrincipal, shift.getId(), AdminKycReviewRequest.builder().action("REJECT").notes("QA").build());
        assertThat(facilityWallet.getLockedEscrowBalance()).isEqualByComparingTo("0");
        assertThat(facilityWallet.getAvailableBalance()).isEqualByComparingTo("108755.20");
    }

    @Test
    void actualHoursReconcileGstForShortDutyAndOvertime() {
        for (int hours : new int[] {6, 10}) {
            LocumShift shift = candidateShift(); shift.setPlatformFeeGstPercent(ShiftCharges.GST_PERCENT); shift.setPlatformFeeGstAmount(new BigDecimal("115.20"));
            ShiftClaim claim = application(shift); claim.setStatus("TIMESHEET_SUBMITTED");
            facilityWallet.setFirstCompletedShiftId(UUID.randomUUID());
            facilityWallet.setAvailableBalance(new BigDecimal("100000")); facilityWallet.setLockedEscrowBalance(new BigDecimal("8755.20"));
            CompleteShiftRequest request = new CompleteShiftRequest(); request.setActualHours(hours);
            shiftClaimService.completeShift(facilityPrincipal, claim.getId(), request);
            assertThat(claim.getHospitalFeeGstAmount()).isEqualByComparingTo(hours == 6 ? "86.40" : "144.00");
            assertThat(facilityWallet.getLockedEscrowBalance()).isEqualByComparingTo(hours == 6 ? "6566.40" : "10944.00");
            assertThat(facilityWallet.getAvailableBalance()).isEqualByComparingTo(hours == 6 ? "102188.80" : "97811.20");
        }
    }
    @Test
    void fullDisputeRefundIncludesGstAndCannotPublishAnUnfundedOpening() {
        LocumShift shift = candidateShift(); shift.setPlatformFeeGstPercent(ShiftCharges.GST_PERCENT); shift.setPlatformFeeGstAmount(new BigDecimal("115.20"));
        ShiftClaim claim = application(shift); claim.setStatus("DISPUTED");
        shift.setSlotsClaimed(1);
        facilityWallet.setLockedEscrowBalance(new BigDecimal("8755.20"));
        adminService.resolveShiftDispute(adminPrincipal, claim.getId(), ResolveDisputeRequest.builder().resolution("REFUND_HOSPITAL").build());
        assertThat(facilityWallet.getAvailableBalance()).isEqualByComparingTo("108755.20");
        assertThat(facilityWallet.getLockedEscrowBalance()).isEqualByComparingTo("0");
        assertThat(shift.getStatus()).isEqualTo("CANCELLED");
        assertThat(shift.getPlatformFeeGstAmount()).isEqualByComparingTo("0");
    }

    @Test
    void cannotRefundAnAlreadyDisbursedClaimFromOtherEscrowFunds() {
        LocumShift shift = candidateShift(); ShiftClaim claim = application(shift);
        claim.setStatus("DISPUTED"); claim.setPayoutStatus("DISBURSED");
        facilityWallet.setLockedEscrowBalance(new BigDecimal("50000"));
        assertThatThrownBy(() -> adminService.resolveShiftDispute(adminPrincipal, claim.getId(),
                ResolveDisputeRequest.builder().resolution("REFUND_HOSPITAL").build()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("already been disbursed");
        assertThat(facilityWallet.getAvailableBalance()).isEqualByComparingTo("100000");
        assertThat(facilityWallet.getLockedEscrowBalance()).isEqualByComparingTo("50000");
    }

}
