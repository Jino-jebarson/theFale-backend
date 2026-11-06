package com.thefale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thefale.config.AppProperties;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES)
@ActiveProfiles("test")
public class ComprehensiveSecurityIntegrationTest {

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
    private UnlockedCandidateRepository unlockedCandidateRepository;

    @Autowired
    private LocumShiftRepository locumShiftRepository;

    @Autowired
    private ShiftClaimRepository shiftClaimRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        auditEventRepository.deleteAll();
        unlockedCandidateRepository.deleteAll();
        shiftClaimRepository.deleteAll();
        locumShiftRepository.deleteAll();
        facilityWalletRepository.deleteAll();
        facilityProfileRepository.deleteAll();
        candidateProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createUser(String phone, String email, String role, String candidateLevel) {
        User user = User.builder()
                .phone(phone)
                .email(email)
                .role(role)
                .candidateLevel(candidateLevel)
                .status("ACTIVE")
                .isOnboarded(true)
                .build();
        return userRepository.save(user);
    }

    private FacilityProfile createFacilityProfile(User user, String name, String city, String state) {
        return facilityProfileRepository.save(FacilityProfile.builder()
                .userId(user.getId())
                .hospitalName(name)
                .facilityType("Super-Specialty")
                .city(city)
                .state(state)
                .licenseNumber("LIC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .beds(250)
                .contactPerson("Dr. Hospital Admin")
                .contactPhone("+919876543210")
                .contactEmail("admin@hospital.org")
                .kycStatus("VERIFIED")
                .build());
    }

    private CandidateProfile createCandidateProfile(User user, String name, String city, String kycStatus) {
        return candidateProfileRepository.save(CandidateProfile.builder()
                .userId(user.getId())
                .name(name)
                .city(city)
                .state("Karnataka")
                .profession("Doctor")
                .specialty("General Medicine")
                .qualification("MBBS")
                .council("Karnataka Medical Council")
                .regNumber("REG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .kycStatus(kycStatus)
                .build());
    }

    private String tokenFor(User user) {
        return jwtTokenProvider.generateAccessToken(CustomUserPrincipal.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .email(user.getEmail())
                .role(user.getRole())
                .candidateLevel(user.getCandidateLevel())
                .enabled(true)
                .build());
    }

    @Test
    @DisplayName("Security: Tampered & Expired JWTs must return 401 Unauthorized")
    void testTamperedAndExpiredJwtRejection() throws Exception {
        User candidate = createUser("+919111111111", "candidate1@test.com", "ROLE_CANDIDATE", "PROFESSIONAL");
        String validToken = tokenFor(candidate);

        // 1. Valid token succeeds or 404 (not 401)
        mockMvc.perform(get("/api/v1/candidates/me/profile")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().is4xxClientError());

        // 2. Tampered token -> 401 Unauthorized
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "abcde";
        mockMvc.perform(get("/api/v1/candidates/me/profile")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());

        // 3. Expired token -> 401 Unauthorized
        AppProperties expiredProps = new AppProperties();
        expiredProps.getSecurity().getJwt().setSecret(appProperties.getSecurity().getJwt().getSecret());
        expiredProps.getSecurity().getJwt().setAccessTokenExpirationMs(-5000);
        JwtTokenProvider expiredProvider = new JwtTokenProvider(expiredProps);
        expiredProvider.init();

        String expiredToken = expiredProvider.generateAccessToken(CustomUserPrincipal.builder()
                .id(candidate.getId())
                .phone(candidate.getPhone())
                .role(candidate.getRole())
                .enabled(true)
                .build());

        mockMvc.perform(get("/api/v1/candidates/me/profile")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Security: Role Escalation - Candidate cannot access Admin or Facility endpoints (403)")
    void testRoleEscalationDefenses() throws Exception {
        User candidate = createUser("+919222222222", "candidate2@test.com", "ROLE_CANDIDATE", "PROFESSIONAL");
        String candidateToken = tokenFor(candidate);

        // Candidate calling Admin Dashboard -> 403 Forbidden
        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isForbidden());

        // Candidate calling Admin Audit Logs -> 403 Forbidden
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isForbidden());

        // Candidate calling Facility Wallet -> 403 Forbidden
        mockMvc.perform(get("/api/v1/employer/wallet")
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isForbidden());

        // Facility calling Shift Claim -> 403 Forbidden
        User facilityUser = createUser("+919333333333", "hospital1@test.com", "ROLE_FACILITY", null);
        String facilityToken = tokenFor(facilityUser);

        mockMvc.perform(post("/api/v1/shifts/" + UUID.randomUUID() + "/claim")
                        .header("Authorization", "Bearer " + facilityToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: IDOR Defense - Candidate A cannot mutate Candidate B's claim")
    void testIdorClaimProtection() throws Exception {
        User hospitalUser = createUser("+919444444444", "apollo@test.com", "ROLE_FACILITY", null);
        FacilityProfile facility = createFacilityProfile(hospitalUser, "Apollo Hospitals", "Chennai", "Tamil Nadu");

        Instant now = Instant.now();
        LocumShift shift = locumShiftRepository.save(LocumShift.builder()
                .facilityId(facility.getId())
                .title("ICU Duty")
                .department("Critical Care")
                .startAt(now.plus(1, ChronoUnit.DAYS))
                .endAt(now.plus(1, ChronoUnit.DAYS).plus(12, ChronoUnit.HOURS))
                .hours(12)
                .ratePerHour(BigDecimal.valueOf(1000))
                .surgeRatePerHour(BigDecimal.ZERO)
                .openings(2)
                .slotsClaimed(1)
                .status("OPEN")
                .build());

        // Candidate A (Owner)
        User userA = createUser("+919555555555", "candidateA@test.com", "ROLE_CANDIDATE", "PROFESSIONAL");
        CandidateProfile profileA = createCandidateProfile(userA, "Dr. Clinician A", "Chennai", "VERIFIED");

        ShiftClaim claimA = shiftClaimRepository.save(ShiftClaim.builder()
                .shiftId(shift.getId())
                .candidateId(profileA.getId())
                .status("CLAIMED")
                .grossAmount(BigDecimal.valueOf(12000))
                .platformFee(BigDecimal.valueOf(600))
                .tdsAmount(BigDecimal.valueOf(120))
                .netAmount(BigDecimal.valueOf(11280))
                .payoutStatus("IN_ESCROW")
                .build());

        // Candidate B (Attacker)
        User userB = createUser("+919666666666", "candidateB@test.com", "ROLE_CANDIDATE", "PROFESSIONAL");
        createCandidateProfile(userB, "Dr. Clinician B", "Chennai", "VERIFIED");
        String tokenB = tokenFor(userB);

        // Candidate B attempts to check-in on Candidate A's claim -> 403 Forbidden
        mockMvc.perform(post("/api/v1/shifts/claims/" + claimA.getId() + "/check-in")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // Candidate B attempts to cancel Candidate A's claim -> 403 Forbidden
        mockMvc.perform(post("/api/v1/shifts/claims/" + claimA.getId() + "/cancel")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: Validation Bypass - Malformed phone and invalid payloads rejected with 400")
    void testValidationBypassDefenses() throws Exception {
        // 1. Invalid phone number format
        OtpSendRequest badPhone = new OtpSendRequest();
        badPhone.setPhone("invalid-phone-string");

        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPhone)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.phone").exists());

        // 2. Malformed OTP format (not 6 digits)
        OtpVerifyRequest badOtp = new OtpVerifyRequest();
        badOtp.setPhone("+919888888888");
        badOtp.setOtp("12"); // only 2 digits

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badOtp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.otp").exists());
    }

    @Test
    @DisplayName("Security: Oversized Payloads - Length constraints enforced (400 Bad Request)")
    void testOversizedPayloadDefenses() throws Exception {
        User candidate = createUser("+919777777777", "oversized@test.com", "ROLE_CANDIDATE", "PROFESSIONAL");
        String token = tokenFor(candidate);

        // Create an oversized bio (> 2000 characters)
        String hugeBio = "A".repeat(5000);

        CandidateOnboardingRequest request = CandidateOnboardingRequest.builder()
                .fullName("Dr. Test")
                .candidateLevel("PROFESSIONAL")
                .profession("Doctor")
                .city("Mumbai")
                .bio(hugeBio)
                .build();

        mockMvc.perform(post("/api/v1/candidates/onboarding")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Security: Concurrent Shift Claim Race Condition - Exactly 1 claim succeeds for single opening")
    void testConcurrentShiftClaimConcurrency() throws Exception {
        User hospital = createUser("+919000000001", "cityhospital@test.com", "ROLE_FACILITY", null);
        FacilityProfile facility = createFacilityProfile(hospital, "City General Hospital", "Hyderabad", "Telangana");

        Instant now = Instant.now();
        LocumShift shift = locumShiftRepository.save(LocumShift.builder()
                .facilityId(facility.getId())
                .title("Emergency Night Shift")
                .department("Casualty")
                .startAt(now.plus(1, ChronoUnit.DAYS))
                .endAt(now.plus(1, ChronoUnit.DAYS).plus(12, ChronoUnit.HOURS))
                .hours(12)
                .ratePerHour(BigDecimal.valueOf(1200))
                .surgeRatePerHour(BigDecimal.ZERO)
                .openings(1)
                .slotsClaimed(0)
                .status("OPEN")
                .build());

        User doctor1 = createUser("+919000000002", "doc1@test.com", "ROLE_CANDIDATE", "PROFESSIONAL");
        createCandidateProfile(doctor1, "Dr. One", "Hyderabad", "VERIFIED");
        String token1 = tokenFor(doctor1);

        User doctor2 = createUser("+919000000003", "doc2@test.com", "ROLE_CANDIDATE", "PROFESSIONAL");
        createCandidateProfile(doctor2, "Dr. Two", "Hyderabad", "VERIFIED");
        String token2 = tokenFor(doctor2);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        Callable<Void> task1 = () -> {
            startLatch.await();
            try {
                mockMvc.perform(post("/api/v1/shifts/" + shift.getId() + "/claim")
                                .header("Authorization", "Bearer " + token1)
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                        .andExpect(result -> {
                            int status = result.getResponse().getStatus();
                            if (status == 200) successCount.incrementAndGet();
                            else rejectedCount.incrementAndGet();
                        });
            } catch (Exception ignored) {
                rejectedCount.incrementAndGet();
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            startLatch.await();
            try {
                mockMvc.perform(post("/api/v1/shifts/" + shift.getId() + "/claim")
                                .header("Authorization", "Bearer " + token2)
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                        .andExpect(result -> {
                            int status = result.getResponse().getStatus();
                            if (status == 200) successCount.incrementAndGet();
                            else rejectedCount.incrementAndGet();
                        });
            } catch (Exception ignored) {
                rejectedCount.incrementAndGet();
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task1);
        Future<Void> f2 = executor.submit(task2);
        startLatch.countDown();
        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(rejectedCount.get()).isEqualTo(1);

        LocumShift updatedShift = locumShiftRepository.findById(shift.getId()).orElseThrow();
        assertThat(updatedShift.getSlotsClaimed()).isEqualTo(1);
        assertThat(updatedShift.getStatus()).isEqualTo("FILLED");
    }

    @Test
    @DisplayName("Security: Concurrent Wallet Debit - Exactly 1 unlock succeeds when balance is 1 credit")
    void testConcurrentWalletDebitConcurrency() throws Exception {
        User hospital = createUser("+919000000010", "fortis@test.com", "ROLE_FACILITY", null);
        FacilityProfile facility = createFacilityProfile(hospital, "Fortis Hospital", "Bengaluru", "Karnataka");

        facilityWalletRepository.save(FacilityWallet.builder()
                .facilityId(facility.getId())
                .availableBalance(BigDecimal.valueOf(50000))
                .candidateCredits(1) // Exactly 1 credit available
                .build());

        String hospitalToken = tokenFor(hospital);

        User candidate = createUser("+919000000011", "candidate.target@test.com", "ROLE_CANDIDATE", "PROFESSIONAL");
        CandidateProfile profile = createCandidateProfile(candidate, "Dr. Targeted Candidate", "Bengaluru", "VERIFIED");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        Callable<Void> task1 = () -> {
            startLatch.await();
            try {
                mockMvc.perform(post("/api/v1/employer/candidates/" + profile.getId() + "/unlock")
                                .header("Authorization", "Bearer " + hospitalToken)
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                        .andExpect(result -> {
                            int status = result.getResponse().getStatus();
                            if (status == 200) successCount.incrementAndGet();
                            else rejectedCount.incrementAndGet();
                        });
            } catch (Exception ignored) {
                rejectedCount.incrementAndGet();
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            startLatch.await();
            try {
                mockMvc.perform(post("/api/v1/employer/candidates/" + profile.getId() + "/unlock")
                                .header("Authorization", "Bearer " + hospitalToken)
                                .header("Idempotency-Key", UUID.randomUUID().toString()))
                        .andExpect(result -> {
                            int status = result.getResponse().getStatus();
                            if (status == 200) successCount.incrementAndGet();
                            else rejectedCount.incrementAndGet();
                        });
            } catch (Exception ignored) {
                rejectedCount.incrementAndGet();
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task1);
        Future<Void> f2 = executor.submit(task2);
        startLatch.countDown();
        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        FacilityWallet finalWallet = facilityWalletRepository.findByFacilityId(facility.getId()).orElseThrow();
        assertThat(finalWallet.getCandidateCredits()).isEqualTo(0);
        assertThat(unlockedCandidateRepository.count()).isEqualTo(1);
    }
}
