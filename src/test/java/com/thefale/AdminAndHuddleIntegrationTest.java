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
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES)
public class AdminAndHuddleIntegrationTest {

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
    private LocumShiftRepository locumShiftRepository;

    @Autowired
    private ShiftClaimRepository shiftClaimRepository;

    @Autowired
    private StudentUpgradeRequestRepository studentUpgradeRequestRepository;

    @Autowired
    private CandidateLevelHistoryRepository candidateLevelHistoryRepository;

    @Autowired
    private HuddlePostRepository huddlePostRepository;

    @Autowired
    private HuddleCommentRepository huddleCommentRepository;

    @Autowired
    private HuddleFlagRepository huddleFlagRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User adminUser;
    private String adminToken;

    private User clinicianUser;
    private CandidateProfile clinicianProfile;
    private String clinicianToken;

    @BeforeEach
    void setUp() {
        huddleFlagRepository.deleteAll();
        huddleCommentRepository.deleteAll();
        huddlePostRepository.deleteAll();
        candidateLevelHistoryRepository.deleteAll();
        studentUpgradeRequestRepository.deleteAll();
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

        // Admin User
        adminUser = userRepository.save(User.builder()
                .email("compliance.admin@fale.care")
                .role("ROLE_ADMIN")
                .status("ACTIVE")
                .build());

        adminToken = jwtTokenProvider.generateAccessToken(CustomUserPrincipal.builder()
                .id(adminUser.getId())
                .email(adminUser.getEmail())
                .role(adminUser.getRole())
                .enabled(true)
                .build());

        // Clinician User
        clinicianUser = userRepository.save(User.builder()
                .phone("+91" + salt.substring(0, 4) + "4001")
                .role("ROLE_CANDIDATE")
                .candidateLevel("PROFESSIONAL")
                .status("ACTIVE")
                .isOnboarded(true)
                .build());

        clinicianProfile = candidateProfileRepository.save(CandidateProfile.builder()
                .userId(clinicianUser.getId())
                .name("Dr. Arvind Swaminathan")
                .city("Chennai")
                .profession("Doctor / General Physician")
                .specialty("Medicine")
                .build());

        clinicianToken = jwtTokenProvider.generateAccessToken(CustomUserPrincipal.builder()
                .id(clinicianUser.getId())
                .phone(clinicianUser.getPhone())
                .role(clinicianUser.getRole())
                .candidateLevel(clinicianUser.getCandidateLevel())
                .enabled(true)
                .build());
    }

    @Test
    @DisplayName("PHI Filter Enforcement: Rejects patient identifiers and accepts anonymized clinical discussions")
    void testPhiFilterEnforcement() throws Exception {
        // 1. Rejects Phone Number in post
        CreateHuddlePostRequest phoneReq = CreateHuddlePostRequest.builder()
                .title("Urgent blood donor needed in ward 4")
                .body("Patient family contact is +919876543210 please reach out immediately.")
                .type("Case")
                .field("Medicine")
                .build();

        mockMvc.perform(post("/api/v1/huddle/posts")
                        .header("Authorization", "Bearer " + clinicianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(phoneReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Direct telephone number detected")));

        // 2. Rejects Aadhaar in post
        CreateHuddlePostRequest aadhaarReq = CreateHuddlePostRequest.builder()
                .title("Govt scheme Ayushman Bharat case study")
                .body("Patient card verification 5544 3322 1100 was submitted.")
                .type("Case")
                .field("Medicine")
                .build();

        mockMvc.perform(post("/api/v1/huddle/posts")
                        .header("Authorization", "Bearer " + clinicianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aadhaarReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("12-digit Aadhaar")));

        // 3. Rejects Hospital MRN in post
        CreateHuddlePostRequest mrnReq = CreateHuddlePostRequest.builder()
                .title("Complex ICU post-op extubation case")
                .body("Reviewing file MRN-994102 for antibiotic stewardship escalation.")
                .type("Case")
                .field("Medicine")
                .build();

        mockMvc.perform(post("/api/v1/huddle/posts")
                        .header("Authorization", "Bearer " + clinicianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mrnReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Medical Record Number")));

        // 4. Accepts properly anonymized clinical case
        CreateHuddlePostRequest validReq = CreateHuddlePostRequest.builder()
                .title("Refractory Septic Shock in a 58-Year-Old Diabetic Patient")
                .body("Case Presentation: 58yo with uncontrolled T2D presented with acute hypotension refractory to crystalloids. Initiated norepinephrine infusion with arterial line monitoring.")
                .type("Case")
                .field("Medicine")
                .build();

        mockMvc.perform(post("/api/v1/huddle/posts")
                        .header("Authorization", "Bearer " + clinicianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Refractory Septic Shock in a 58-Year-Old Diabetic Patient"))
                .andExpect(jsonPath("$.data.status").value("LIVE"));
    }

    @Test
    @DisplayName("Huddle Community Interactions: Upvotes, Comments, and Post Flagging")
    void testHuddleInteractionsAndFlagging() throws Exception {
        // Create initial post
        HuddlePost post = huddlePostRepository.save(HuddlePost.builder()
                .authorId(clinicianUser.getId())
                .title("Best practices for arterial line insertion in obese patients")
                .body("Sharing clinical tips: utilize vascular ultrasound with high-frequency probe for radial access.")
                .type("Experience")
                .field("Nursing")
                .status("LIVE")
                .upvotes(0)
                .flagCount(0)
                .build());

        // 1. Upvote post
        mockMvc.perform(post("/api/v1/huddle/posts/" + post.getId() + "/upvote")
                        .header("Authorization", "Bearer " + clinicianToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upvotes").value(1));

        // 2. Add comment
        CreateHuddleCommentRequest commentReq = CreateHuddleCommentRequest.builder()
                .content("Excellent technique tip. We routinely use dynamic needle tip positioning.")
                .build();

        mockMvc.perform(post("/api/v1/huddle/posts/" + post.getId() + "/comments")
                        .header("Authorization", "Bearer " + clinicianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorName").value("Dr. Arvind Swaminathan"));

        // 3. Flag post
        FlagHuddlePostRequest flagReq = FlagHuddlePostRequest.builder()
                .reason("Suspected commercial promotion / off-label mention")
                .build();

        mockMvc.perform(post("/api/v1/huddle/posts/" + post.getId() + "/flag")
                        .header("Authorization", "Bearer " + clinicianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(flagReq)))
                .andExpect(status().isOk());

        // Duplicate flag by same user returns 409
        mockMvc.perform(post("/api/v1/huddle/posts/" + post.getId() + "/flag")
                        .header("Authorization", "Bearer " + clinicianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(flagReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Admin Queue: Student Graduation Upgrade Approval and Audit Trail")
    void testStudentGraduationUpgradeApproval() throws Exception {
        // Create student user & profile
        User student = userRepository.save(User.builder()
                .phone("+919988776655")
                .role("ROLE_CANDIDATE")
                .candidateLevel("STUDENT")
                .status("ACTIVE")
                .isOnboarded(true)
                .build());

        CandidateProfile candProfile = candidateProfileRepository.save(CandidateProfile.builder()
                .userId(student.getId())
                .name("Karthik Sharma")
                .city("Bengaluru")
                .college("Bangalore Medical College")
                .course("MBBS")
                .build());

        // Submit upgrade request
        StudentUpgradeRequest upgrade = studentUpgradeRequestRepository.save(StudentUpgradeRequest.builder()
                .candidateId(candProfile.getId())
                .qualification("MBBS")
                .council("Karnataka Medical Council")
                .regNumber("KMC-2026-9001")
                .status("PENDING")
                .build());

        // 1. Admin views pending upgrade queue
        mockMvc.perform(get("/api/v1/admin/candidates/upgrades")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].regNumber").value("KMC-2026-9001"));

        // 2. Admin approves upgrade
        AdminKycReviewRequest reviewReq = AdminKycReviewRequest.builder()
                .action("APPROVE")
                .notes("Verified with Karnataka Medical Council portal registration records.")
                .build();

        mockMvc.perform(post("/api/v1/admin/candidates/upgrades/" + upgrade.getId() + "/review")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewReq)))
                .andExpect(status().isOk());

        // 3. User is now PROFESSIONAL
        User updatedUser = userRepository.findById(student.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("PROFESSIONAL", updatedUser.getCandidateLevel());

        // 4. Candidate level history audit trail recorded
        var history = candidateLevelHistoryRepository.findByCandidateIdOrderByCreatedAtDesc(candProfile.getId());
        org.junit.jupiter.api.Assertions.assertEquals(1, history.size());
        org.junit.jupiter.api.Assertions.assertEquals("STUDENT", history.get(0).getFromLevel());
        org.junit.jupiter.api.Assertions.assertEquals("PROFESSIONAL", history.get(0).getToLevel());
    }

    @Test
    @DisplayName("Admin Shift Payout Disbursement: Marks claim paid with banking UTR and reconciles escrow")
    void testAdminShiftPayoutDisbursement() throws Exception {
        // Setup facility and wallet
        User facUser = userRepository.save(User.builder()
                .phone("+919911223344")
                .role("ROLE_FACILITY")
                .status("ACTIVE")
                .isOnboarded(true)
                .build());

        FacilityProfile facility = facilityProfileRepository.save(FacilityProfile.builder()
                .userId(facUser.getId())
                .hospitalName("Columbia Asia Hospital")
                .facilityType("Multi-Specialty")
                .city("Bengaluru")
                .state("Karnataka")
                .beds(150)
                .licenseNumber("KA-HOSP-2023-1100")
                .contactPerson("Dr. Satish")
                .contactPhone("+919876543200")
                .contactEmail("admin@columbia.org")
                .build());

        FacilityWallet wallet = facilityWalletRepository.save(FacilityWallet.builder()
                .facilityId(facility.getId())
                .availableBalance(BigDecimal.valueOf(10000.00))
                .lockedEscrowBalance(BigDecimal.valueOf(4500.00))
                .totalDisbursed(BigDecimal.ZERO)
                .candidateCredits(5)
                .build());

        LocumShift shift = locumShiftRepository.save(LocumShift.builder()
                .facilityId(facility.getId())
                .title("ICU Night Duty Staff Nurse")
                .department("Critical care")
                .startAt(Instant.now().minus(2, java.time.temporal.ChronoUnit.DAYS))
                .endAt(Instant.now().minus(2, java.time.temporal.ChronoUnit.DAYS).plus(10, java.time.temporal.ChronoUnit.HOURS))
                .hours(10)
                .ratePerHour(BigDecimal.valueOf(450.00))
                .openings(1)
                .slotsClaimed(1)
                .status("COMPLETED")
                .build());

        ShiftClaim claim = shiftClaimRepository.save(ShiftClaim.builder()
                .shiftId(shift.getId())
                .candidateId(clinicianProfile.getId())
                .status("COMPLETED")
                .grossAmount(BigDecimal.valueOf(4500.00))
                .platformFee(BigDecimal.valueOf(225.00))
                .tdsAmount(BigDecimal.valueOf(45.00))
                .netAmount(BigDecimal.valueOf(4230.00))
                .payoutStatus("PENDING_DISBURSEMENT")
                .payoutSlipRef("PAY-2026-FALE-1002")
                .build());

        // 1. Admin views pending payouts
        mockMvc.perform(get("/api/v1/admin/payouts/pending")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].netAmountPaise").value(423000L));

        // 2. Admin disburses payout with UTR
        AdminPayoutApprovalRequest disburseReq = AdminPayoutApprovalRequest.builder()
                .utr("CMS20261101994182")
                .notes("Disbursed via Cashfree Nodal Payout API")
                .build();

        mockMvc.perform(post("/api/v1/admin/payouts/" + claim.getId() + "/disburse")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disburseReq)))
                .andExpect(status().isOk());

        // 3. Claim is DISBURSED
        ShiftClaim updatedClaim = shiftClaimRepository.findById(claim.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("DISBURSED", updatedClaim.getPayoutStatus());
        org.junit.jupiter.api.Assertions.assertEquals("CMS20261101994182", updatedClaim.getUtr());

        // 4. Facility wallet locked escrow decremented to 0, total disbursed incremented to 4500.00
        FacilityWallet updatedWallet = facilityWalletRepository.findById(wallet.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(updatedWallet.getLockedEscrowBalance()));
        org.junit.jupiter.api.Assertions.assertEquals(0, BigDecimal.valueOf(4500.00).compareTo(updatedWallet.getTotalDisbursed()));
    }
}
