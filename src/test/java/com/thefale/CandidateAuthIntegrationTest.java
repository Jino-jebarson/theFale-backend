package com.thefale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thefale.dto.*;
import com.thefale.entity.OtpVerification;
import com.thefale.repository.OtpVerificationRepository;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES)
public class CandidateAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OtpVerificationRepository otpVerificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("End-to-end OTP Authentication, Token Refresh, and Candidate Profile Onboarding")
    void testAuthAndCandidateOnboarding() throws Exception {
        String testPhone = "+919876500001";

        // 1. Send OTP
        OtpSendRequest sendReq = OtpSendRequest.builder()
                .phone(testPhone)
                .role("candidate")
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 2. Fetch seeded verification and set a deterministic OTP for testing
        OtpVerification verification = otpVerificationRepository
                .findTopByContactAndVerifiedFalseOrderByCreatedAtDesc(testPhone)
                .orElseThrow();
        verification.setOtpHash(passwordEncoder.encode("123456"));
        otpVerificationRepository.save(verification);

        // 3. Verify OTP with wrong code -> 400 Bad Request
        OtpVerifyRequest badVerify = OtpVerifyRequest.builder()
                .phone(testPhone)
                .otp("999999")
                .build();

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badVerify)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));

        // 4. Verify OTP with correct code -> 200 OK
        OtpVerifyRequest goodVerify = OtpVerifyRequest.builder()
                .phone(testPhone)
                .otp("123456")
                .build();

        MvcResult authResult = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goodVerify)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.phone").value(testPhone))
                .andReturn();

        String responseJson = authResult.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(responseJson).path("data").path("accessToken").asText();
        Cookie refreshCookie = authResult.getResponse().getCookie("fale_refresh_token");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();

        // 5. Rotate refresh token
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // 6. Complete Graduate Onboarding
        CandidateOnboardingRequest graduateOnboard = CandidateOnboardingRequest.builder()
                .fullName("Dr. Vikram Rathore")
                .email("dr.vikram@hospital.org")
                .city("Hyderabad")
                .state("Telangana")
                .candidateLevel("PROFESSIONAL")
                .profession("Doctor / General Physician")
                .primarySpecialty("Cardiology")
                .qualification("MBBS, MD")
                .council("Telangana State Medical Council")
                .councilRegistrationNumber("TSMC-2019-4412")
                .regYear("2019")
                .experienceYears("5 Years")
                .bio("Consultant interventional cardiologist")
                .expectedSalaryMinPaise(15000000L)
                .build();

        mockMvc.perform(post("/api/v1/candidate/onboarding")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(graduateOnboard)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Dr. Vikram Rathore"))
                .andExpect(jsonPath("$.data.candidateLevel").value("PROFESSIONAL"))
                .andExpect(jsonPath("$.data.isStudent").value(false))
                .andExpect(jsonPath("$.data.regNumber").value("TSMC-2019-4412"));

        // 7. Get Profile
        mockMvc.perform(get("/api/v1/candidate/profile")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.city").value("Hyderabad"))
                .andExpect(jsonPath("$.data.profession").value("Doctor / General Physician"));

        // 8. Save Payout Method (UPI)
        CandidatePayoutMethodDto payoutDto = CandidatePayoutMethodDto.builder()
                .methodType("upi")
                .upiId("vikram@okhdfcbank")
                .accountHolderName("Dr Vikram Rathore")
                .build();

        mockMvc.perform(post("/api/v1/candidate/payout-methods")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payoutDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.methodType").value("upi"))
                .andExpect(jsonPath("$.data.status").value("Verified"));
    }

    @Test
    @DisplayName("Healthcare Student Onboarding & Upgrade Request Workflow")
    void testStudentOnboardingAndUpgradeRequest() throws Exception {
        String studentPhone = "+919876500002";

        // 1. Send OTP & set code
        mockMvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(OtpSendRequest.builder().phone(studentPhone).role("candidate").build())))
                .andExpect(status().isOk());

        OtpVerification verification = otpVerificationRepository
                .findTopByContactAndVerifiedFalseOrderByCreatedAtDesc(studentPhone)
                .orElseThrow();
        verification.setOtpHash(passwordEncoder.encode("654321"));
        otpVerificationRepository.save(verification);

        // 2. Verify OTP
        MvcResult authResult = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(OtpVerifyRequest.builder().phone(studentPhone).otp("654321").build())))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(authResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 3. Student Onboarding (Notice: council registration fields omitted!)
        CandidateOnboardingRequest studentOnboard = CandidateOnboardingRequest.builder()
                .fullName("Pooja Nair (Final Year Student)")
                .email("pooja.student@fale.edu")
                .city("Chennai")
                .state("Tamil Nadu")
                .candidateLevel("STUDENT")
                .course("MBBS")
                .collegeName("Stanley Medical College, Chennai")
                .studyYear("4th Year (Intern)")
                .expectedGraduationYear(2027)
                .bonafideCertificateUrl("https://storage.thefale.com/docs/bonafide-pooja.pdf")
                .build();

        mockMvc.perform(post("/api/v1/candidate/onboarding")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(studentOnboard)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Pooja Nair (Final Year Student)"))
                .andExpect(jsonPath("$.data.candidateLevel").value("STUDENT"))
                .andExpect(jsonPath("$.data.isStudent").value(true))
                .andExpect(jsonPath("$.data.college").value("Stanley Medical College, Chennai"));

        // 4. Student submits graduation upgrade request
        StudentUpgradeRequestDto upgradeReq = StudentUpgradeRequestDto.builder()
                .qualification("MBBS")
                .council("Tamil Nadu Medical Council")
                .regNumber("TNMC-2027-PROV-1182")
                .degreeCertificateUrl("https://storage.thefale.com/docs/provisional-pooja.pdf")
                .build();

        mockMvc.perform(post("/api/v1/candidate/upgrade-request")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upgradeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.regNumber").value("TNMC-2027-PROV-1182"));
    }
}
