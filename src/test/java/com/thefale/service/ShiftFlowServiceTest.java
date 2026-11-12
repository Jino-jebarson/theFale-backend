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
                auditLogService
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
                platformSettingRepository
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
    void facilityFirstShiftRequiresApprovalWithZeroFee_secondShiftAutoLiveWithFivePercentFee() {
        PostShiftRequest req = PostShiftRequest.builder()
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
        assertThat(first.getFacilityFeePercent()).isEqualTo(0.0);
        assertThat(first.getFacilityFeePaise()).isEqualTo(0L);
        assertThat(facilityWallet.getLockedEscrowBalance()).isEqualByComparingTo("4000.00");

        // 2nd shift -> OPEN auto-live + 5% facility fee (Rs 200 on Rs 4000)
        when(locumShiftRepository.countValidFeeShiftsByFacility(facilityId)).thenReturn(1L);
        when(locumShiftRepository.countApprovedShiftsByFacility(facilityId)).thenReturn(1L);
        when(candidateProfileRepository.findAll()).thenReturn(Collections.emptyList());

        LocumShiftDto second = shiftManagementService.postShift(facilityPrincipal, req);

        assertThat(second.getStatus()).isEqualTo("OPEN");
        assertThat(second.isAutoApproved()).isTrue();
        assertThat(second.getFacilityFeePercent()).isEqualTo(5.0);
        assertThat(second.getFacilityFeePaise()).isEqualTo(20000L);
        assertThat(facilityWallet.getLockedEscrowBalance()).isEqualByComparingTo("8200.00");
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

        PostShiftRequest increaseOpenings = PostShiftRequest.builder()
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
        PostShiftRequest dropRate = PostShiftRequest.builder()
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
    void claimShiftAppliesZeroFeeOnFirstShiftAndFivePercentFromSecondShift() {
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
        assertThat(secondClaim.getPlatformFeePercent()).isEqualTo(5.0);
        assertThat(secondClaim.getPlatformFeePaise()).isEqualTo(40000L);
        // Gross = 8000, 5% fee = 400, 1% TDS = 80 -> Net = 7520
        assertThat(secondClaim.getNetAmountPaise()).isEqualTo(752000L);
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
                .checkInPin("4829")
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
        assertThatThrownBy(() -> shiftClaimService.checkIn(candidatePrincipal, claimId, new ShiftCheckInRequest("0000")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid 4-digit");

        // Correct PIN transitions to CHECKED_IN
        ShiftClaimDto checkedIn = shiftClaimService.checkIn(candidatePrincipal, claimId, new ShiftCheckInRequest("4829"));
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
        assertThat(claim.getPlatformFee()).isEqualByComparingTo("300.00");
        assertThat(claim.getNetAmount()).isEqualByComparingTo("5640.00");
        // Hospital refund for 2 unused hrs: 2000 pay + 100 facility fee = 2100
        assertThat(facilityWallet.getAvailableBalance()).isEqualByComparingTo(availBefore.add(new BigDecimal("2100.00")));
        assertThat(facilityWallet.getLockedEscrowBalance()).isEqualByComparingTo("6300.00");
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
}
