package com.thefale.security;

import com.thefale.config.AppProperties;
import com.thefale.dto.AuthResponse;
import com.thefale.dto.OtpVerifyRequest;
import com.thefale.entity.OtpVerification;
import com.thefale.entity.User;
import com.thefale.exception.BadRequestException;
import com.thefale.repository.OtpVerificationRepository;
import com.thefale.repository.RefreshTokenRepository;
import com.thefale.repository.UserRepository;
import com.thefale.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceProductionSecurityTest {

    @Mock
    private Environment environment;

    @Test
    @DisplayName("Fail-Safe: Production profile REFUSES to start if dev-otp-enabled is true")
    void prodProfileRefusesDevOtpEnabled() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        AppProperties appProperties = new AppProperties();
        appProperties.getAuth().setDevOtpEnabled(true); // UNSAFE DEV SETTING

        AuthService authService = new AuthService(
                null, null, null, null, null, appProperties, environment
        );

        assertThatThrownBy(authService::validateProductionSecurity)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FATAL SECURITY MISCONFIGURATION: Dev OTP must NOT be enabled in the 'prod' profile!");
    }

    @Test
    @DisplayName("Fail-Safe: Production profile REFUSES non-standard access token lifetime")
    void prodProfileRefusesNonStandardTokenLifetime() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        AppProperties appProperties = new AppProperties();
        appProperties.getAuth().setDevOtpEnabled(false);
        appProperties.getSecurity().getJwt().setAccessTokenExpirationMs(60000); // 1 minute (non-standard)

        AuthService authService = new AuthService(
                null, null, null, null, null, appProperties, environment
        );

        assertThatThrownBy(authService::validateProductionSecurity)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FATAL SECURITY MISCONFIGURATION: Production profile refuses non-standard JWT access token lifetime");
    }

    @Test
    @DisplayName("Production profile starts cleanly when dev-otp is false and access token lifetime is standard 15m (900000ms)")
    void prodProfileStartsCleanlyWithProductionStandards() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        AppProperties appProperties = new AppProperties();
        appProperties.getAuth().setDevOtpEnabled(false);
        appProperties.getSecurity().getJwt().setAccessTokenExpirationMs(900000); // Standard 15 minutes

        AuthService authService = new AuthService(
                null, null, null, null, null, appProperties, environment
        );

        assertThatCode(authService::validateProductionSecurity).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Dev profile starts cleanly with dev-otp enabled and custom token lifetime")
    void devProfilePermitsDevOtpAndShortLifetimes() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        AppProperties appProperties = new AppProperties();
        appProperties.getAuth().setDevOtpEnabled(true);
        appProperties.getSecurity().getJwt().setAccessTokenExpirationMs(5000); // 5 seconds for refresh testing

        AuthService authService = new AuthService(
                null, null, null, null, null, appProperties, environment
        );

        assertThatCode(authService::validateProductionSecurity).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("OTP Rule: Dynamic random OTP matches hash and authenticates user in dev profile")
    void dynamicOtpVerifiesSuccessfullyWhenHashMatches() {
        AppProperties appProperties = new AppProperties();
        appProperties.getAuth().setDevOtpEnabled(true);

        UserRepository userRepository = mock(UserRepository.class);
        OtpVerificationRepository otpRepo = mock(OtpVerificationRepository.class);
        RefreshTokenRepository refreshRepo = mock(RefreshTokenRepository.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        User testUser = User.builder()
                .id(UUID.randomUUID())
                .phone("+919876543210")
                .role("ROLE_CANDIDATE")
                .candidateLevel("STUDENT")
                .isOnboarded(true)
                .status("ACTIVE")
                .build();

        OtpVerification verification = OtpVerification.builder()
                .contact("+919876543210")
                .otpHash("hashed_random_492018")
                .attempts(0)
                .expiresAt(Instant.now().plusSeconds(300))
                .verified(false)
                .build();

        when(otpRepo.findTopByContactAndVerifiedFalseOrderByCreatedAtDesc("+919876543210"))
                .thenReturn(Optional.of(verification));
        when(passwordEncoder.matches("492018", "hashed_random_492018")).thenReturn(true);
        when(userRepository.findByPhone("+919876543210")).thenReturn(Optional.of(testUser));
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("mock.jwt.token");

        AuthService authService = new AuthService(
                userRepository, otpRepo, refreshRepo, jwtTokenProvider, passwordEncoder, appProperties, environment
        );

        OtpVerifyRequest verifyReq = OtpVerifyRequest.builder()
                .phone("+919876543210")
                .otp("492018")
                .build();

        AuthResponse authResponse = authService.verifyOtp(verifyReq, response);

        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getRole()).isEqualTo("ROLE_CANDIDATE");
        assertThat(authResponse.getCandidateLevel()).isEqualTo("STUDENT");
        assertThat(authResponse.isOnboarded()).isTrue();
    }

    @Test
    @DisplayName("OTP Rule: Fixed code 123456 is REJECTED in dev profile without matching hash")
    void fixedCodeIsRejectedInDevWithoutMatchingHash() {
        AppProperties appProperties = new AppProperties();
        appProperties.getAuth().setDevOtpEnabled(true);

        UserRepository userRepository = mock(UserRepository.class);
        OtpVerificationRepository otpRepo = mock(OtpVerificationRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        OtpVerification verification = OtpVerification.builder()
                .contact("+919876543210")
                .otpHash("hashed_random_684920")
                .attempts(0)
                .expiresAt(Instant.now().plusSeconds(300))
                .verified(false)
                .build();

        when(otpRepo.findTopByContactAndVerifiedFalseOrderByCreatedAtDesc("+919876543210"))
                .thenReturn(Optional.of(verification));
        when(passwordEncoder.matches("123456", "hashed_random_684920")).thenReturn(false);

        AuthService authService = new AuthService(
                userRepository, otpRepo, null, null, passwordEncoder, appProperties, environment
        );

        OtpVerifyRequest verifyReq = OtpVerifyRequest.builder()
                .phone("+919876543210")
                .otp("123456") // Attempting fixed code bypass
                .build();

        assertThatThrownBy(() -> authService.verifyOtp(verifyReq, response))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid verification code");
    }

    @Test
    @DisplayName("OTP Rule: Prod profile strictly rejects any non-matching OTP")
    void prodProfileRejectsNonMatchingOtp() {
        AppProperties appProperties = new AppProperties();
        appProperties.getAuth().setDevOtpEnabled(false);

        UserRepository userRepository = mock(UserRepository.class);
        OtpVerificationRepository otpRepo = mock(OtpVerificationRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        OtpVerification verification = OtpVerification.builder()
                .contact("+919999911111")
                .otpHash("hashed_real_prod_sms_code")
                .attempts(0)
                .expiresAt(Instant.now().plusSeconds(300))
                .verified(false)
                .build();

        when(otpRepo.findTopByContactAndVerifiedFalseOrderByCreatedAtDesc("+919999911111"))
                .thenReturn(Optional.of(verification));
        when(passwordEncoder.matches("123456", "hashed_real_prod_sms_code")).thenReturn(false);

        AuthService authService = new AuthService(
                userRepository, otpRepo, null, null, passwordEncoder, appProperties, environment
        );

        OtpVerifyRequest verifyReq = OtpVerifyRequest.builder()
                .phone("+919999911111")
                .otp("123456") // Attempting fixed bypass in prod
                .build();

        assertThatThrownBy(() -> authService.verifyOtp(verifyReq, response))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid verification code");
    }
}
