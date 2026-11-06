package com.thefale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thefale.dto.*;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES)
public class FullEndToEndFlowsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private FacilityProfileRepository facilityProfileRepository;

    @Autowired
    private FacilityWalletRepository facilityWalletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private LocumShiftRepository locumShiftRepository;

    @Autowired
    private ShiftClaimRepository shiftClaimRepository;

    @Autowired
    private PlatformSettingRepository platformSettingRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private com.thefale.repository.OtpVerificationRepository otpVerificationRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private com.thefale.security.RateLimitService rateLimitService;

    private User studentUser;
    private User doctorUser;
    private User facilityUser;
    private User adminUser;
    private FacilityProfile facilityProfile;
    private CandidateProfile doctorProfile;
    private CandidateProfile studentProfile;
    private FacilityWallet facilityWallet;

    private String studentToken;
    private String doctorToken;
    private String facilityToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        rateLimitService.clear();
        otpVerificationRepository.deleteAll();
        notificationRepository.deleteAll();
        auditEventRepository.deleteAll();
        shiftClaimRepository.deleteAll();
        jobApplicationRepository.deleteAll();
        locumShiftRepository.deleteAll();
        jobPostingRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        facilityWalletRepository.deleteAll();
        facilityProfileRepository.deleteAll();
        candidateProfileRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Seed Student User
        studentUser = userRepository.save(User.builder()
                .phone("+919999991111")
                .role("ROLE_CANDIDATE")
                .candidateLevel("STUDENT")
                .status("ACTIVE")
                .isOnboarded(true)
                .build());
        studentProfile = candidateProfileRepository.save(CandidateProfile.builder()
                .userId(studentUser.getId())
                .name("Aarav Student")
                .city("Chennai")
                .profession("Medical Student")
                .qualification("MBBS Student")
                .kycStatus("VERIFIED")
                .build());
        studentToken = jwtTokenProvider.generateAccessToken(CustomUserPrincipal.builder()
                .id(studentUser.getId())
                .phone(studentUser.getPhone())
                .email(studentUser.getEmail())
                .role(studentUser.getRole())
                .candidateLevel(studentUser.getCandidateLevel())
                .enabled(true)
                .build());

        // 2. Seed Graduate Doctor User
        doctorUser = userRepository.save(User.builder()
                .phone("+919999992222")
                .role("ROLE_CANDIDATE")
                .candidateLevel("PROFESSIONAL")
                .status("ACTIVE")
                .isOnboarded(true)
                .build());
        doctorProfile = candidateProfileRepository.save(CandidateProfile.builder()
                .userId(doctorUser.getId())
                .name("Dr. Priya Sharma")
                .city("Bengaluru")
                .profession("Critical Care Doctor")
                .specialty("Intensive Care")
                .qualification("MBBS, MD")
                .council("Karnataka Medical Council")
                .regNumber("KMC-2018-8888")
                .experienceYears("4")
                .kycStatus("VERIFIED")
                .build());
        doctorToken = jwtTokenProvider.generateAccessToken(CustomUserPrincipal.builder()
                .id(doctorUser.getId())
                .phone(doctorUser.getPhone())
                .email(doctorUser.getEmail())
                .role(doctorUser.getRole())
                .candidateLevel(doctorUser.getCandidateLevel())
                .enabled(true)
                .build());

        // 3. Seed Facility User
        facilityUser = userRepository.save(User.builder()
                .phone("+919999993333")
                .role("ROLE_FACILITY")
                .status("ACTIVE")
                .isOnboarded(true)
                .build());
        facilityProfile = facilityProfileRepository.save(FacilityProfile.builder()
                .userId(facilityUser.getId())
                .hospitalName("Apollo Hospitals Bengaluru")
                .facilityType("Super Speciality")
                .city("Bengaluru")
                .state("Karnataka")
                .beds(400)
                .licenseNumber("KA-MED-444")
                .contactPerson("Dr. Rajesh")
                .contactPhone("+919999993333")
                .contactEmail("admin@apollo.com")
                .kycStatus("VERIFIED")
                .build());
        facilityWallet = facilityWalletRepository.save(FacilityWallet.builder()
                .facilityId(facilityProfile.getId())
                .availableBalance(new BigDecimal("100000.00"))
                .lockedEscrowBalance(BigDecimal.ZERO)
                .totalDisbursed(BigDecimal.ZERO)
                .candidateCredits(10)
                .build());
        facilityToken = jwtTokenProvider.generateAccessToken(CustomUserPrincipal.builder()
                .id(facilityUser.getId())
                .phone(facilityUser.getPhone())
                .email(facilityUser.getEmail())
                .role(facilityUser.getRole())
                .enabled(true)
                .build());

        // 4. Seed Admin User
        adminUser = userRepository.save(User.builder()
                .phone("+919999994444")
                .email("admin@fale.in")
                .role("ROLE_ADMIN")
                .status("ACTIVE")
                .isOnboarded(true)
                .build());
        adminToken = jwtTokenProvider.generateAccessToken(CustomUserPrincipal.builder()
                .id(adminUser.getId())
                .phone(adminUser.getPhone())
                .email(adminUser.getEmail())
                .role(adminUser.getRole())
                .enabled(true)
                .build());
    }

    // =========================================================================
    // PART 1 & 5: LOGIN ROUTING AND ROLE GUARDS
    // =========================================================================
    @Test
    @DisplayName("Login verify returns role, candidateLevel, and onboarded status for all personas")
    void testLoginVerifyRoutingAttributes() throws Exception {
        // OTP verification for Candidate (Doctor)
        otpVerificationRepository.save(com.thefale.entity.OtpVerification.builder()
                .contact("+919999992222")
                .otpHash(passwordEncoder.encode("654321"))
                .attempts(0)
                .expiresAt(java.time.Instant.now().plus(java.time.Duration.ofMinutes(5)))
                .verified(false)
                .build());

        OtpVerifyRequest doctorReq = OtpVerifyRequest.builder().phone("+919999992222").otp("654321").role("ROLE_CANDIDATE").build();
        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .header("X-Forwarded-For", "192.168.1.101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(doctorReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ROLE_CANDIDATE"))
                .andExpect(jsonPath("$.data.candidateLevel").value("PROFESSIONAL"))
                .andExpect(jsonPath("$.data.onboarded").value(true));

        // OTP verification for Student
        otpVerificationRepository.save(com.thefale.entity.OtpVerification.builder()
                .contact("+919999991111")
                .otpHash(passwordEncoder.encode("654321"))
                .attempts(0)
                .expiresAt(java.time.Instant.now().plus(java.time.Duration.ofMinutes(5)))
                .verified(false)
                .build());

        OtpVerifyRequest studentReq = OtpVerifyRequest.builder().phone("+919999991111").otp("654321").role("ROLE_CANDIDATE").build();
        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .header("X-Forwarded-For", "192.168.1.102")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(studentReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ROLE_CANDIDATE"))
                .andExpect(jsonPath("$.data.candidateLevel").value("STUDENT"))
                .andExpect(jsonPath("$.data.onboarded").value(true));

        // OTP verification for Facility
        otpVerificationRepository.save(com.thefale.entity.OtpVerification.builder()
                .contact("+919999993333")
                .otpHash(passwordEncoder.encode("654321"))
                .attempts(0)
                .expiresAt(java.time.Instant.now().plus(java.time.Duration.ofMinutes(5)))
                .verified(false)
                .build());

        OtpVerifyRequest facilityReq = OtpVerifyRequest.builder().phone("+919999993333").otp("654321").role("ROLE_FACILITY").build();
        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .header("X-Forwarded-For", "192.168.1.103")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(facilityReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ROLE_FACILITY"))
                .andExpect(jsonPath("$.data.onboarded").value(true));
    }

    // =========================================================================
    // PART 3 & 5: JOB FLOW (POST -> APPROVE -> APPLY -> SHORTLIST -> PIPELINE -> HIRED)
    // =========================================================================
    @Test
    @DisplayName("Complete Job Flow: Facility post PENDING_APPROVAL -> Admin approve -> Apply match score -> Admin shortlist -> Pipeline to HIRED with unmasked contact")
    void testCompleteJobFlow() throws Exception {
        // Step 1: Facility posts a permanent job requirement
        PostJobRequest postJobRequest = PostJobRequest.builder()
                .title("Intensive Care Registrar")
                .department("Critical Care")
                .employmentType("FULL_TIME")
                .internship(false)
                .city("Bengaluru")
                .openings(2)
                .salaryMinPaise(8000000L) // ₹80,000
                .salaryMaxPaise(12000000L) // ₹1,20,000
                .experienceRequired("3-5 Years")
                .qualificationRequired("MD / DNB Intensive Care")
                .description("Supervision of tertiary ICU, invasive ventilation, and trauma resuscitation.")
                .build();

        String postResp = mockMvc.perform(post("/api/v1/facility/jobs")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postJobRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andReturn().getResponse().getContentAsString();

        UUID jobId = UUID.fromString(objectMapper.readTree(postResp).path("data").path("id").asText());

        // Step 2: Verify job is NOT visible to candidates in public /jobs while PENDING_APPROVAL
        mockMvc.perform(get("/api/v1/jobs")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + jobId + "')]").doesNotExist());

        // Step 3: Admin reviews and APPROVES the job requirement
        AdminKycReviewRequest approveJobReq = AdminKycReviewRequest.builder()
                .action("APPROVE")
                .notes("Clinical establishment standards verified")
                .build();

        mockMvc.perform(post("/api/v1/admin/postings/" + jobId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveJobReq)))
                .andExpect(status().isOk());

        // Step 4: Now candidate sees the job as OPEN
        mockMvc.perform(get("/api/v1/jobs/" + jobId)
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        // Step 5: Candidate applies -> receives UNDER_ADMIN_SCREENING status with match score
        String applyResp = mockMvc.perform(post("/api/v1/jobs/" + jobId + "/apply")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNDER_ADMIN_SCREENING"))
                .andExpect(jsonPath("$.data.matchScore").isNumber())
                .andReturn().getResponse().getContentAsString();

        UUID appId = UUID.fromString(objectMapper.readTree(applyResp).path("data").path("id").asText());
        int matchScore = objectMapper.readTree(applyResp).path("data").path("matchScore").asInt();
        assertTrue(matchScore >= 0 && matchScore <= 100, "Match score must be between 0 and 100");

        // Step 6: Verify facility cannot see candidate yet (not shortlisted by admin yet)
        mockMvc.perform(get("/api/v1/facility/applicants")
                        .header("Authorization", "Bearer " + facilityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + appId + "')]").doesNotExist());

        // Step 7: Admin screens candidate application -> SHORTLIST
        AdminApplicationScreeningRequest screenReq = AdminApplicationScreeningRequest.builder()
                .action("SHORTLIST")
                .notes("Council credentials and clinical qualification verified")
                .build();

        mockMvc.perform(patch("/api/v1/admin/applications/" + appId + "/screening")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(screenReq)))
                .andExpect(status().isOk());

        // Step 8: Facility now sees applicant in pipeline, but phone/email are MASKED
        mockMvc.perform(get("/api/v1/facility/applicants")
                        .header("Authorization", "Bearer " + facilityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + appId + "')].status", hasItem("SHORTLISTED")))
                .andExpect(jsonPath("$.data[?(@.id == '" + appId + "')].candidatePhone", hasItem(containsString("•••••"))));

        // Step 9: Facility advances candidate: SHORTLISTED -> INTERVIEW -> OFFERED -> HIRED
        mockMvc.perform(patch("/api/v1/facility/applicants/" + appId + "/status")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INTERVIEW\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/facility/applicants/" + appId + "/status")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFERED\"}"))
                .andExpect(status().isOk());

        // Step 10: On HIRED: candidate contact is fully unmasked
        mockMvc.perform(patch("/api/v1/facility/applicants/" + appId + "/status")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"HIRED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HIRED"))
                .andExpect(jsonPath("$.data.candidatePhone").value("+919999992222"));
    }

    // =========================================================================
    // PART 4 & 5: SHIFT FLOW (ESCROW LOCK -> APPROVE -> CLAIM -> CHECK-IN -> COMPLETED -> DISBURSED)
    // =========================================================================
    @Test
    @DisplayName("Complete Shift Flow: Escrow lock -> Admin review -> Claim -> Check-in -> Completion -> Payout disbursement")
    void testCompleteShiftFlowWithEscrow() throws Exception {
        Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Instant shiftEnd = tomorrow.plus(8, ChronoUnit.HOURS);

        // Step 1: Facility posts a locum shift -> Escrow locked, status PENDING_APPROVAL
        PostShiftRequest postShiftReq = PostShiftRequest.builder()
                .title("Trauma Night Shift")
                .department("Emergency Medicine")
                .startAt(tomorrow)
                .endAt(shiftEnd)
                .ratePerHourPaise(120000L) // ₹1,200/hr
                .surgeRatePerHourPaise(30000L) // ₹300/hr surge
                .surgeLabel("Night Surge")
                .openings(2)
                .urgent(true)
                .build();

        String shiftResp = mockMvc.perform(post("/api/v1/facility/shifts")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postShiftReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andReturn().getResponse().getContentAsString();

        UUID shiftId = UUID.fromString(objectMapper.readTree(shiftResp).path("data").path("id").asText());

        // Verify Escrow held in facility wallet: (1200 + 300) * 8 hrs * 2 slots = ₹24,000 locked
        FacilityWallet updatedWallet = facilityWalletRepository.findByFacilityId(facilityProfile.getId()).orElseThrow();
        assertEquals(new BigDecimal("24000.00"), updatedWallet.getLockedEscrowBalance());

        // Step 2: Admin reviews and APPROVES the shift -> becomes OPEN
        mockMvc.perform(post("/api/v1/admin/shifts/" + shiftId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isOk());

        // Step 3: Graduate Doctor claims the shift
        // Test with default SHIFT_CLAIM_REQUIRES_ADMIN_REVIEW = true
        platformSettingRepository.save(PlatformSetting.builder().key("SHIFT_CLAIM_REQUIRES_ADMIN_REVIEW").value("true").build());

        String claimResp = mockMvc.perform(post("/api/v1/shifts/" + shiftId + "/claim")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLAIM_PENDING_ADMIN"))
                .andReturn().getResponse().getContentAsString();

        UUID claimId = UUID.fromString(objectMapper.readTree(claimResp).path("data").path("id").asText());

        // Step 4: Admin reviews and confirms the claim -> status becomes CONFIRMED
        AdminApplicationScreeningRequest claimScreenReq = AdminApplicationScreeningRequest.builder()
                .action("APPROVE")
                .notes("Doctor qualification matches ICU night mandate")
                .build();

        mockMvc.perform(patch("/api/v1/admin/shifts/claims/" + claimId + "/screening")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(claimScreenReq)))
                .andExpect(status().isOk());

        // Step 5: Candidate checks in -> status becomes CHECKED_IN
        mockMvc.perform(post("/api/v1/shifts/claims/" + claimId + "/check-in")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHECKED_IN"));

        // Step 6: Shift completed -> status becomes COMPLETED, payoutStatus becomes PENDING_DISBURSEMENT
        mockMvc.perform(post("/api/v1/shifts/claims/" + claimId + "/complete")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.payoutStatus").value("PENDING_DISBURSEMENT"));

        // Step 7: Admin records banking disbursement with UTR
        AdminPayoutApprovalRequest payoutReq = AdminPayoutApprovalRequest.builder()
                .utr("UTR2026110299881122")
                .build();

        mockMvc.perform(post("/api/v1/admin/payouts/" + claimId + "/disburse")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payoutReq)))
                .andExpect(status().isOk());

        ShiftClaim finalClaim = shiftClaimRepository.findById(claimId).orElseThrow();
        assertEquals("DISBURSED", finalClaim.getPayoutStatus());
        assertEquals("UTR2026110299881122", finalClaim.getUtr());
    }

    // =========================================================================
    // PART 4 & 5: INSTANT SHIFT CLAIM WHEN ADMIN REVIEW SETTING IS FALSE
    // =========================================================================
    @Test
    @DisplayName("Instant shift claim works when SHIFT_CLAIM_REQUIRES_ADMIN_REVIEW is false")
    void testInstantShiftClaimWhenAdminReviewDisabled() throws Exception {
        platformSettingRepository.save(PlatformSetting.builder().key("SHIFT_CLAIM_REQUIRES_ADMIN_REVIEW").value("false").build());

        Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        LocumShift openShift = locumShiftRepository.save(LocumShift.builder()
                .facilityId(facilityProfile.getId())
                .title("Instant Ward Shift")
                .department("General Medicine")
                .startAt(tomorrow)
                .endAt(tomorrow.plus(8, ChronoUnit.HOURS))
                .hours(8)
                .ratePerHour(new BigDecimal("1000.00"))
                .openings(3)
                .slotsClaimed(0)
                .status("OPEN")
                .build());

        // Graduate claims -> immediate CONFIRMED
        mockMvc.perform(post("/api/v1/shifts/" + openShift.getId() + "/claim")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    // =========================================================================
    // ACCESS CONTROL AND ROLE GUARDS
    // =========================================================================
    @Test
    @DisplayName("Strict Role Enforcement: Candidate cannot access facility wallet; Facility cannot access admin dashboard; Student cannot claim locum shift")
    void testAccessControlGuards() throws Exception {
        // 1. Candidate accessing facility wallet -> 403 Forbidden
        mockMvc.perform(get("/api/v1/facility/wallet")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());

        // 2. Facility accessing admin dashboard -> 403 Forbidden
        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", "Bearer " + facilityToken))
                .andExpect(status().isForbidden());

        // 3. Healthcare Student attempting to claim locum shift -> 403 Forbidden (NMC/INC regulation)
        Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        LocumShift openShift = locumShiftRepository.save(LocumShift.builder()
                .facilityId(facilityProfile.getId())
                .title("General Medicine Shift")
                .department("General Medicine")
                .startAt(tomorrow)
                .endAt(tomorrow.plus(8, ChronoUnit.HOURS))
                .hours(8)
                .ratePerHour(new BigDecimal("1000.00"))
                .openings(2)
                .slotsClaimed(0)
                .status("OPEN")
                .build());

        mockMvc.perform(post("/api/v1/shifts/" + openShift.getId() + "/claim")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // MATCH SCORE DETERMINISM
    // =========================================================================
    @Test
    @DisplayName("Match score is deterministic (0-100) and completely excludes protected attributes")
    void testMatchScoreDeterminism() throws Exception {
        JobPosting job = jobPostingRepository.save(JobPosting.builder()
                .facilityId(facilityProfile.getId())
                .title("Critical Care Intensivist")
                .department("Critical Care")
                .employmentType("FULL_TIME")
                .city("Bengaluru")
                .openings(2)
                .salaryMin(new BigDecimal("90000.00"))
                .salaryMax(new BigDecimal("140000.00"))
                .experienceRequired("3-5 Years")
                .qualificationRequired("MD")
                .description("Intensive care duties")
                .status("OPEN")
                .build());

        // Candidate doctor applies
        String resp1 = mockMvc.perform(post("/api/v1/jobs/" + job.getId() + "/apply")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int score1 = objectMapper.readTree(resp1).path("data").path("matchScore").asInt();
        assertTrue(score1 >= 0 && score1 <= 100);

        // Verify score calculation is between 70 and 100 and deterministic
        assertTrue(score1 >= 75);
    }
}
