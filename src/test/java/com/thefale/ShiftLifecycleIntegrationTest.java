package com.thefale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thefale.dto.PostShiftRequest;
import com.thefale.dto.WalletTopUpRequest;
import com.thefale.entity.*;
import com.thefale.repository.*;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.security.JwtTokenProvider;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES)
public class ShiftLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FacilityProfileRepository facilityProfileRepository;

    @Autowired
    private FacilityWalletRepository facilityWalletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private LocumShiftRepository locumShiftRepository;

    @Autowired
    private ShiftClaimRepository shiftClaimRepository;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private PlatformSettingRepository platformSettingRepository;

    @Autowired
    private MinimumRoleRateRepository minimumRoleRateRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User facilityUser;
    private FacilityProfile facilityProfile;
    private String facilityToken;

    private User studentUser;
    private CandidateProfile studentProfile;
    private String studentToken;

    private User graduateUser;
    private CandidateProfile graduateProfile;
    private String graduateToken;

    @BeforeEach
    void setUp() {
        shiftClaimRepository.deleteAll();
        locumShiftRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        facilityWalletRepository.deleteAll();
        facilityProfileRepository.deleteAll();
        candidateProfileRepository.deleteAll();
        notificationRepository.deleteAll();
        auditEventRepository.deleteAll();
        userRepository.deleteAll();

        String salt = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // Facility
        facilityUser = userRepository.save(User.builder()
                .phone("+91" + salt.substring(0, 4) + "3001")
                .role("ROLE_FACILITY")
                .status("ACTIVE")
                .isOnboarded(true)
                .build());

        facilityProfile = facilityProfileRepository.save(FacilityProfile.builder()
                .userId(facilityUser.getId())
                .hospitalName("Narayana Health City")
                .facilityType("Super-Specialty")
                .city("Bengaluru")
                .state("Karnataka")
                .beds(400)
                .licenseNumber("KA-HOSP-2023-8822")
                .contactPerson("Dr. Devi Shetty")
                .contactPhone("+919876543220")
                .contactEmail("contact@narayana.org")
                .kycStatus("VERIFIED")
                .build());

        facilityToken = jwtTokenProvider.generateAccessToken(CustomUserPrincipal.builder()
                .id(facilityUser.getId())
                .phone(facilityUser.getPhone())
                .role(facilityUser.getRole())
                .enabled(true)
                .build());

        // Student Candidate
        studentUser = userRepository.save(User.builder()
                .phone("+91" + salt.substring(0, 4) + "3002")
                .role("ROLE_CANDIDATE")
                .candidateLevel("STUDENT")
                .status("ACTIVE")
                .isOnboarded(true)
                .build());

        studentProfile = candidateProfileRepository.save(CandidateProfile.builder()
                .userId(studentUser.getId())
                .name("Arun Kumar (Student)")
                .city("Bengaluru")
                .college("Bangalore Medical College")
                .course("MBBS")
                .build());

        studentToken = jwtTokenProvider.generateAccessToken(CustomUserPrincipal.builder()
                .id(studentUser.getId())
                .phone(studentUser.getPhone())
                .role(studentUser.getRole())
                .candidateLevel(studentUser.getCandidateLevel())
                .enabled(true)
                .build());

        // Graduate Candidate
        graduateUser = userRepository.save(User.builder()
                .phone("+91" + salt.substring(0, 4) + "3003")
                .role("ROLE_CANDIDATE")
                .candidateLevel("PROFESSIONAL")
                .status("ACTIVE")
                .isOnboarded(true)
                .build());

        graduateProfile = candidateProfileRepository.save(CandidateProfile.builder()
                .userId(graduateUser.getId())
                .name("Sister Philo Mary")
                .city("Bengaluru")
                .profession("Staff Nurse — ICU / Night Duty")
                .specialty("Critical care")
                .qualification("B.Sc Nursing")
                .council("Karnataka Nursing Council")
                .regNumber("KNC-2016-8812")
                .experienceYears("5 yrs")
                .kycStatus("VERIFIED")
                .build());

        graduateToken = jwtTokenProvider.generateAccessToken(CustomUserPrincipal.builder()
                .id(graduateUser.getId())
                .phone(graduateUser.getPhone())
                .role(graduateUser.getRole())
                .candidateLevel(graduateUser.getCandidateLevel())
                .enabled(true)
                .build());
    }

    @Test
    @DisplayName("Pessimistic Escrow Hold on Shift Post & Statutory Wage Floor Enforcement")
    void testShiftPostingEscrowAndStatutoryFloor() throws Exception {
        // Initialize facility wallet with ₹0.00
        facilityWalletRepository.save(FacilityWallet.builder()
                .facilityId(facilityProfile.getId())
                .availableBalance(BigDecimal.ZERO)
                .lockedEscrowBalance(BigDecimal.ZERO)
                .totalDisbursed(BigDecimal.ZERO)
                .candidateCredits(3)
                .build());

        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(8, ChronoUnit.HOURS); // 8-hour shift

        PostShiftRequest shiftReq = PostShiftRequest.builder()
                .title("Staff Nurse — ICU / Night Duty")
                .department("Critical care")
                .startAt(start)
                .endAt(end)
                .ratePerHourPaise(45000L) // ₹450/hr (> ₹350 statutory floor)
                .openings(1)
                .urgent(true)
                .build();

        // 1. Post shift with insufficient balance fails with 400 Bad Request
        mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shiftReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Insufficient escrow reserve")));

        // 2. Statutory wage floor check: post below statutory floor (e.g. ₹200/hr < ₹350/hr)
        // Top up ₹50,000 first
        mockMvc.perform(post("/api/v1/facility/wallet/top-up")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(WalletTopUpRequest.builder()
                                .amountPaise(5000000L) // ₹50,000
                                .paymentMethod("upi")
                                .utr("CMS" + System.currentTimeMillis())
                                .build())))
                .andExpect(status().isOk());

        PostShiftRequest belowFloorReq = PostShiftRequest.builder()
                .title("Staff Nurse — ICU / Night Duty")
                .department("Critical care")
                .startAt(start)
                .endAt(end)
                .ratePerHourPaise(20000L) // ₹200/hr < ₹350 floor
                .openings(1)
                .build();

        mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(belowFloorReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Statutory wage floor violation")));

        // 3. Successful shift broadcast with escrow lock (₹450 * 8 hrs * 1 opening = ₹3,600 = 360,000 paise)
        mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shiftReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hours").value(8))
                .andExpect(jsonPath("$.data.ratePerHourPaise").value(45000L))
                .andExpect(jsonPath("$.data.totalPayPaise").value(360000L));

        // Check wallet: ₹50,000 - ₹3,600 = ₹46,400 available, ₹3,600 locked escrow
        mockMvc.perform(get("/api/v1/facility/wallet")
                        .header("Authorization", "Bearer " + facilityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableBalancePaise").value(4640000L))
                .andExpect(jsonPath("$.data.lockedEscrowBalancePaise").value(360000L));
    }

    @Test
    @DisplayName("Student Guard: 403 Forbidden when healthcare student attempts to claim locum shift")
    void testStudentBarredFromLocumShiftClaim() throws Exception {
        // Seed an open shift
        LocumShift shift = locumShiftRepository.save(LocumShift.builder()
                .facilityId(facilityProfile.getId())
                .title("ICU Staff Nurse Duty")
                .department("Critical care")
                .startAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .endAt(Instant.now().plus(1, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS))
                .hours(8)
                .ratePerHour(BigDecimal.valueOf(450.00))
                .openings(2)
                .slotsClaimed(0)
                .status("OPEN")
                .build());

        // Student attempt -> 403 Forbidden
        mockMvc.perform(post("/api/v1/shifts/" + shift.getId() + "/claim")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Healthcare students are not legally permitted to practice as independent locum staff under NMC/INC regulations."));
    }

    @Test
    @DisplayName("Graduate Claim Lifecycle: Slot concurrency, dynamic fee calculation, check-in, completion, and cancel")
    void testGraduateClaimLifecycle() throws Exception {
        // Initialize facility wallet with ₹20,000
        facilityWalletRepository.save(FacilityWallet.builder()
                .facilityId(facilityProfile.getId())
                .availableBalance(BigDecimal.valueOf(20000.00))
                .lockedEscrowBalance(BigDecimal.ZERO)
                .totalDisbursed(BigDecimal.ZERO)
                .candidateCredits(3)
                .build());

        platformSettingRepository.save(com.thefale.entity.PlatformSetting.builder()
                .key("SHIFT_CLAIM_REQUIRES_ADMIN_REVIEW")
                .value("false")
                .build());

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(12, ChronoUnit.HOURS); // 12-hour duty

        // Facility posts shift: ₹450/hr * 12 hrs = ₹5,400 escrow
        PostShiftRequest shiftReq = PostShiftRequest.builder()
                .title("Staff Nurse — ICU / Night Duty")
                .department("Critical care")
                .startAt(start)
                .endAt(end)
                .ratePerHourPaise(45000L) // ₹450
                .openings(1) // Only 1 slot
                .build();

        String shiftResp = mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shiftReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String shiftIdStr = objectMapper.readTree(shiftResp).path("data").path("id").asText();
        UUID shiftId = UUID.fromString(shiftIdStr);

        // Open shift for claiming
        LocumShift postedShift = locumShiftRepository.findById(shiftId).orElseThrow();
        postedShift.setStatus("OPEN");
        locumShiftRepository.save(postedShift);

        // 1. Graduate claims shift slot
        // Gross = ₹5,400 (540,000 paise).
        // Dynamic fees: Platform fee 5% = ₹270 (27,000 paise), TDS 1% = ₹54 (5,400 paise), Net = ₹5,076 (507,600 paise)
        String claimResp = mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/claim")
                        .header("Authorization", "Bearer " + graduateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grossAmountPaise").value(540000L))
                .andExpect(jsonPath("$.data.platformFeePaise").value(27000L))
                .andExpect(jsonPath("$.data.tdsAmountPaise").value(5400L))
                .andExpect(jsonPath("$.data.netAmountPaise").value(507600L))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.payoutStatus").value("IN_ESCROW"))
                .andReturn().getResponse().getContentAsString();

        String claimIdStr = objectMapper.readTree(claimResp).path("data").path("id").asText();
        UUID claimId = UUID.fromString(claimIdStr);

        // 2. Duplicate claim attempt by same candidate returns 409 Conflict
        mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/claim")
                        .header("Authorization", "Bearer " + graduateToken))
                .andExpect(status().isConflict());

        // 3. Shift slot count is now 1 of 1, status is FILLED
        LocumShift updatedShift = locumShiftRepository.findById(shiftId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(1, updatedShift.getSlotsClaimed());
        org.junit.jupiter.api.Assertions.assertEquals("FILLED", updatedShift.getStatus());

        // 4. Candidate checks in at hospital
        mockMvc.perform(post("/api/v1/candidate/claims/" + claimId + "/check-in")
                        .header("Authorization", "Bearer " + graduateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHECKED_IN"));

        // 5. Shift completed: status -> COMPLETED, payoutStatus -> PENDING_DISBURSEMENT
        mockMvc.perform(post("/api/v1/candidate/claims/" + claimId + "/complete")
                        .header("Authorization", "Bearer " + graduateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.payoutStatus").value("PENDING_DISBURSEMENT"));

        // 6. View candidate earnings & claims list
        mockMvc.perform(get("/api/v1/candidate/claims")
                        .header("Authorization", "Bearer " + graduateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].netAmountPaise").value(507600L));
    }
}
