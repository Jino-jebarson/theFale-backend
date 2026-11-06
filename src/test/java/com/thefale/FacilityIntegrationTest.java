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
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES)
public class FacilityIntegrationTest {

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
    private HospitalBankAccountRepository hospitalBankAccountRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private UnlockedCandidateRepository unlockedCandidateRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User facilityUser;
    private String facilityToken;
    private User candidateUser;
    private CandidateProfile candidateProfile;
    private String candidateToken;

    @BeforeEach
    void setUp() {
        unlockedCandidateRepository.deleteAll();
        jobApplicationRepository.deleteAll();
        jobPostingRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        facilityWalletRepository.deleteAll();
        hospitalBankAccountRepository.deleteAll();
        facilityProfileRepository.deleteAll();
        candidateProfileRepository.deleteAll();
        notificationRepository.deleteAll();
        auditEventRepository.deleteAll();
        userRepository.deleteAll();

        String salt = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // Create Facility User
        facilityUser = userRepository.save(User.builder()
                .phone("+91" + salt.substring(0, 4) + "1001")
                .role("ROLE_FACILITY")
                .status("ACTIVE")
                .isOnboarded(false)
                .build());

        facilityToken = jwtTokenProvider.generateAccessToken(CustomUserPrincipal.builder()
                .id(facilityUser.getId())
                .phone(facilityUser.getPhone())
                .role(facilityUser.getRole())
                .enabled(true)
                .build());

        // Create Candidate User & Profile
        candidateUser = userRepository.save(User.builder()
                .phone("+91" + salt.substring(0, 4) + "2002")
                .email("dr.rajesh.resdex@fale.care")
                .role("ROLE_CANDIDATE")
                .candidateLevel("PROFESSIONAL")
                .status("ACTIVE")
                .isOnboarded(true)
                .build());

        candidateProfile = candidateProfileRepository.save(CandidateProfile.builder()
                .userId(candidateUser.getId())
                .name("Dr. Rajesh Sen")
                .city("Bengaluru")
                .profession("Doctor / General Physician")
                .specialty("Critical Care")
                .qualification("MBBS, MD")
                .council("Karnataka Medical Council")
                .regNumber("KMC-2018-9941")
                .experienceYears("6 yrs")
                .build());

        candidateToken = jwtTokenProvider.generateAccessToken(CustomUserPrincipal.builder()
                .id(candidateUser.getId())
                .phone(candidateUser.getPhone())
                .role(candidateUser.getRole())
                .candidateLevel(candidateUser.getCandidateLevel())
                .enabled(true)
                .build());
    }

    @Test
    @DisplayName("Facility Onboarding, Profile Retrieval, and Institutional Bank Account Setup")
    void testFacilityOnboardingAndBankSetup() throws Exception {
        FacilityOnboardingRequest onboardReq = FacilityOnboardingRequest.builder()
                .hospitalName("Manipal Super Specialty Hospital")
                .facilityType("Multi-Specialty")
                .city("Bengaluru")
                .state("Karnataka")
                .pincode("560068")
                .addressLine("98 HAL Old Airport Road")
                .beds(350)
                .icuBeds(50)
                .licenseNumber("KA-HOSP-2023-9912")
                .nabhNumber("NABH-2024-0012")
                .accreditationLevel("NABH Accredited")
                .contactPerson("Dr. Rajesh Kumar")
                .contactPhone("+919876543210")
                .contactEmail("admin@manipal.org")
                .build();

        // 1. Onboard
        mockMvc.perform(post("/api/v1/facility/onboard")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(onboardReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hospitalName").value("Manipal Super Specialty Hospital"))
                .andExpect(jsonPath("$.data.beds").value(350));

        // 2. Get Profile
        mockMvc.perform(get("/api/v1/facility/profile")
                        .header("Authorization", "Bearer " + facilityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.city").value("Bengaluru"))
                .andExpect(jsonPath("$.data.accreditationLevel").value("NABH Accredited"));

        // 3. Bank Account Setup
        UpdateHospitalBankRequest bankReq = UpdateHospitalBankRequest.builder()
                .bankName("HDFC Bank Ltd")
                .accountNumber("50200084920194")
                .ifsc("HDFC0000060")
                .branch("Koramangala, Bengaluru")
                .accountHolderName("Manipal Super Specialty Hospital Private Limited")
                .accountType("Current")
                .build();

        mockMvc.perform(put("/api/v1/facility/bank-account")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bankReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bankName").value("HDFC Bank Ltd"))
                .andExpect(jsonPath("$.data.accountNumberMasked").value("•••• •••• 0194"))
                .andExpect(jsonPath("$.data.status").value("Verified"));
    }

    @Test
    @DisplayName("Job Posting and Applicant Status Lifecycle")
    void testJobPostingAndApplicantStatusLifecycle() throws Exception {
        // 1. Onboard Facility first
        FacilityOnboardingRequest onboardReq = FacilityOnboardingRequest.builder()
                .hospitalName("Apollo Health City")
                .facilityType("Super-Specialty")
                .city("Bengaluru")
                .state("Karnataka")
                .beds(250)
                .licenseNumber("KA-HOSP-2023-1188")
                .contactPerson("Dr. Preetha Reddy")
                .contactPhone("+919876543211")
                .contactEmail("contact@apollo.org")
                .build();

        mockMvc.perform(post("/api/v1/facility/onboard")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(onboardReq)))
                .andExpect(status().isOk());

        // 2. Post Permanent Job
        PostJobRequest jobReq = PostJobRequest.builder()
                .title("ICU Staff Nurse — Critical Care")
                .department("Critical Care")
                .employmentType("Full-time")
                .internship(false)
                .city("Bengaluru")
                .openings(3)
                .salaryMinPaise(3500000L) // ₹35,000
                .salaryMaxPaise(5000000L) // ₹50,000
                .experienceRequired("2–5 yrs")
                .description("Provide intensive bedside nursing care in a tertiary multi-specialty ICU.")
                .build();

        String jobResponseStr = mockMvc.perform(post("/api/v1/facility/jobs")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(jobReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("ICU Staff Nurse — Critical Care"))
                .andExpect(jsonPath("$.data.salaryMinPaise").value(3500000L))
                .andReturn().getResponse().getContentAsString();

        String jobIdStr = objectMapper.readTree(jobResponseStr).path("data").path("id").asText();
        UUID jobId = UUID.fromString(jobIdStr);

        // Approve job to OPEN status for applications
        JobPosting postedJob = jobPostingRepository.findById(jobId).orElseThrow();
        postedJob.setStatus("OPEN");
        jobPostingRepository.save(postedJob);

        // 3. Candidate applies to this job
        String appResponseStr = mockMvc.perform(post("/api/v1/jobs/" + jobId + "/apply")
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String appIdStr = objectMapper.readTree(appResponseStr).path("data").path("id").asText();
        UUID applicationId = UUID.fromString(appIdStr);

        // Admin shortlists application to appear in facility pipeline
        JobApplication app = jobApplicationRepository.findById(applicationId).orElseThrow();
        app.setStatus("SHORTLISTED");
        jobApplicationRepository.save(app);

        // 4. Facility views applicants
        mockMvc.perform(get("/api/v1/employer/applications")
                        .header("Authorization", "Bearer " + facilityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].candidateName").value("Dr. Rajesh Sen"))
                .andExpect(jsonPath("$.data[0].status").value("SHORTLISTED"));

        // 5. Facility updates applicant status to INTERVIEW
        UpdateApplicationStatusRequest statusReq = UpdateApplicationStatusRequest.builder()
                .status("INTERVIEW")
                .notes("Profile shortlisted for clinical interview round.")
                .build();

        mockMvc.perform(put("/api/v1/employer/applications/" + applicationId + "/status")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INTERVIEW"));
    }

    @Test
    @DisplayName("Wallet Ledger: UTR Top-Up, Sourcing Credits Purchase, and Resdex Contact Unlock")
    void testWalletTopUpCreditsAndResdexUnlock() throws Exception {
        // 1. Onboard Facility
        FacilityOnboardingRequest onboardReq = FacilityOnboardingRequest.builder()
                .hospitalName("Fortis Hospital")
                .facilityType("Multi-Specialty")
                .city("Bengaluru")
                .state("Karnataka")
                .beds(180)
                .licenseNumber("KA-HOSP-2023-4411")
                .contactPerson("Dr. Ramesh Sen")
                .contactPhone("+919876543212")
                .contactEmail("contact@fortis.org")
                .build();

        mockMvc.perform(post("/api/v1/facility/onboard")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(onboardReq)))
                .andExpect(status().isOk());

        // 2. Check initial wallet balance (₹0.00, 3 free candidate credits)
        mockMvc.perform(get("/api/v1/facility/wallet")
                        .header("Authorization", "Bearer " + facilityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableBalancePaise").value(0L))
                .andExpect(jsonPath("$.data.candidateCredits").value(3));

        // 3. Top-up wallet via manual UTR deposit (₹25,000 = 2,500,000 paise)
        String utrNum = "CMS" + System.currentTimeMillis();
        WalletTopUpRequest topUpReq = WalletTopUpRequest.builder()
                .amountPaise(2500000L)
                .paymentMethod("upi")
                .utr(utrNum)
                .build();

        mockMvc.perform(post("/api/v1/facility/wallet/top-up")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(topUpReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableBalancePaise").value(2500000L));

        // 4. Duplicate UTR rejected with 409 Conflict
        mockMvc.perform(post("/api/v1/facility/wallet/top-up")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(topUpReq)))
                .andExpect(status().isConflict());

        // 5. Purchase candidate sourcing credits (pkg-1: 10 credits for ₹1,999)
        BuyCreditsRequest buyReq = BuyCreditsRequest.builder()
                .packageId("pkg-1")
                .build();

        mockMvc.perform(post("/api/v1/facility/wallet/buy-credits")
                        .header("Authorization", "Bearer " + facilityToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidateCredits").value(13)) // 3 initial + 10 purchased
                .andExpect(jsonPath("$.data.availableBalancePaise").value(2300100L)); // 25000 - 1999 = 23001.00

        // 6. Check ledger transactions
        mockMvc.perform(get("/api/v1/facility/wallet/transactions")
                        .header("Authorization", "Bearer " + facilityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        // 7. Search Resdex candidate directory: candidate contact is masked
        mockMvc.perform(get("/api/v1/employer/candidates")
                        .header("Authorization", "Bearer " + facilityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Dr. Rajesh Sen"))
                .andExpect(jsonPath("$.data[0].isContactUnlocked").value(false))
                .andExpect(jsonPath("$.data[0].phone").value(containsString("•••")));

        // 8. Unlock candidate contact using 1 credit
        mockMvc.perform(post("/api/v1/employer/candidates/" + candidateProfile.getId() + "/unlock")
                        .header("Authorization", "Bearer " + facilityToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isContactUnlocked").value(true))
                .andExpect(jsonPath("$.data.phone").value(candidateUser.getPhone()))
                .andExpect(jsonPath("$.data.email").value("dr.rajesh.resdex@fale.care"));

        // 9. Verify credit balance decremented to 12
        mockMvc.perform(get("/api/v1/facility/wallet")
                        .header("Authorization", "Bearer " + facilityToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidateCredits").value(12));
    }
}
