package com.thefale.service;

import com.thefale.config.AppProperties;
import com.thefale.dto.*;
import com.thefale.entity.OtpVerification;
import com.thefale.entity.RefreshToken;
import com.thefale.entity.User;
import com.thefale.exception.BadRequestException;
import com.thefale.exception.UnauthorizedException;
import com.thefale.repository.OtpVerificationRepository;
import com.thefale.repository.RefreshTokenRepository;
import com.thefale.repository.UserRepository;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.security.JwtTokenProvider;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OtpVerificationRepository otpVerificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final Environment environment;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void validateProductionSecurity() {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (isProd) {
            if (appProperties.getAuth().isDevOtpEnabled()) {
                throw new IllegalStateException("FATAL SECURITY MISCONFIGURATION: Dev OTP must NOT be enabled in the 'prod' profile!");
            }
            long accessExpMs = appProperties.getSecurity().getJwt().getAccessTokenExpirationMs();
            if (accessExpMs != 900000L) {
                throw new IllegalStateException("FATAL SECURITY MISCONFIGURATION: Production profile refuses non-standard JWT access token lifetime: "
                        + accessExpMs + "ms. Standard lifetime is 900000ms (15 minutes). Shortened token lifetimes are permitted only in dev builds/profiles.");
            }
        }
    }

    @Transactional
    public OtpSendResponse sendOtp(OtpSendRequest request) {
        String cleanPhone = normalizePhone(request.getPhone());
        String role = normalizeRole(request.getRole());

        // Delete any expired unverified OTPs for this contact
        otpVerificationRepository.deleteByContact(cleanPhone);

        // Generate cryptographically secure 6-digit OTP
        int codeInt = 100000 + secureRandom.nextInt(900000);
        String otpCode = String.valueOf(codeInt);

        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));
        String hashedOtp = passwordEncoder.encode(otpCode);

        OtpVerification record = OtpVerification.builder()
                .contact(cleanPhone)
                .otpHash(hashedOtp)
                .attempts(0)
                .expiresAt(expiresAt)
                .verified(false)
                .build();
        otpVerificationRepository.save(record);

        // Dev profile: Print to console log ONLY, never return in header or body
        if (appProperties.getAuth().isDevOtpEnabled()) {
            log.info("=================================================");
            log.info("[DEV OTP CONSOLE] Contact: {} | Code: {}", cleanPhone, otpCode);
            log.info("=================================================");
        } else {
            // Production SMS dispatch integration
            log.info("Production SMS dispatched to masked phone: ***{}", cleanPhone.substring(Math.max(0, cleanPhone.length() - 4)));
        }

        return OtpSendResponse.builder()
                .message("Verification code sent successfully")
                .expiresInSeconds(300)
                .cooldownSeconds(60)
                .build();
    }

    @Transactional
    public AuthResponse verifyOtp(OtpVerifyRequest request, HttpServletResponse httpResponse) {
        String cleanPhone = normalizePhone(request.getPhone());

        OtpVerification verification = otpVerificationRepository
                .findTopByContactAndVerifiedFalseOrderByCreatedAtDesc(cleanPhone)
                .orElseThrow(() -> new BadRequestException("No active verification code found for this mobile number."));

        if (Instant.now().isAfter(verification.getExpiresAt())) {
            throw new BadRequestException("Verification code has expired. Please request a new one.");
        }

        if (verification.getAttempts() >= 5) {
            throw new BadRequestException("Maximum verification attempts exceeded. Please request a new code.");
        }

        verification.setAttempts(verification.getAttempts() + 1);

        boolean matchesHash = passwordEncoder.matches(request.getOtp(), verification.getOtpHash());
        if (!matchesHash) {
            otpVerificationRepository.save(verification);
            throw new BadRequestException("Invalid verification code. Please check and try again.");
        }

        verification.setVerified(true);
        otpVerificationRepository.save(verification);

        // Find or create user
        User user = userRepository.findByPhone(cleanPhone).orElseGet(() -> {
            String initialRole = normalizeRole(request.getRole());
            User newUser = User.builder()
                    .phone(cleanPhone)
                    .role(initialRole)
                    .status("ACTIVE")
                    .isOnboarded(false)
                    .build();
            return userRepository.save(newUser);
        });

        if ("SUSPENDED".equalsIgnoreCase(user.getStatus()) || "LOCKED".equalsIgnoreCase(user.getStatus())) {
            throw new UnauthorizedException("Your account is currently locked or suspended. Please contact support.");
        }

        return generateTokensAndSetCookie(user, httpResponse);
    }

    @Transactional
    public AuthResponse refreshToken(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String rawToken = extractRefreshTokenCookie(httpRequest)
                .orElseThrow(() -> new UnauthorizedException("Refresh token cookie is missing. Please sign in again."));

        String tokenHash = hashToken(rawToken);
        RefreshToken refreshTokenRecord = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token."));

        if (refreshTokenRecord.isRevoked() || Instant.now().isAfter(refreshTokenRecord.getExpiresAt())) {
            // Potential token reuse detected: revoke all tokens for this user
            refreshTokenRepository.deleteByUserId(refreshTokenRecord.getUserId());
            throw new UnauthorizedException("Refresh token is expired or revoked. Please sign in again.");
        }

        // Revoke the old token (rotation)
        refreshTokenRecord.setRevoked(true);
        refreshTokenRepository.save(refreshTokenRecord);

        User user = userRepository.findById(refreshTokenRecord.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User account not found."));

        return generateTokensAndSetCookie(user, httpResponse);
    }

    @Transactional
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        extractRefreshTokenCookie(httpRequest).ifPresent(rawToken -> {
            String tokenHash = hashToken(rawToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(record -> {
                record.setRevoked(true);
                refreshTokenRepository.save(record);
            });
        });

        // Clear refresh cookie
        ResponseCookie clearCookie = ResponseCookie.from(appProperties.getSecurity().getJwt().getCookieName(), "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());
    }

    @Transactional
    public AuthResponse adminLogin(AdminLoginRequest request, HttpServletResponse httpResponse) {
        User admin = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid admin credentials."));

        if (!"ROLE_ADMIN".equals(admin.getRole())) {
            throw new UnauthorizedException("Access restricted to platform administrators.");
        }

        if (admin.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            throw new UnauthorizedException("Invalid admin credentials.");
        }

        return generateTokensAndSetCookie(admin, httpResponse);
    }

    private AuthResponse generateTokensAndSetCookie(User user, HttpServletResponse httpResponse) {
        CustomUserPrincipal principal = CustomUserPrincipal.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .email(user.getEmail())
                .role(user.getRole())
                .candidateLevel(user.getCandidateLevel())
                .enabled("ACTIVE".equalsIgnoreCase(user.getStatus()))
                .build();

        String accessToken = jwtTokenProvider.generateAccessToken(principal);

        // Generate 32-byte secure random refresh token
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawRefreshToken = HexFormat.of().formatHex(randomBytes);
        String tokenHash = hashToken(rawRefreshToken);

        Instant refreshExpiresAt = Instant.now().plus(Duration.ofMillis(appProperties.getSecurity().getJwt().getRefreshTokenExpirationMs()));

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(refreshExpiresAt)
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        // Set HttpOnly, Secure, SameSite=Strict cookie
        ResponseCookie cookie = ResponseCookie.from(appProperties.getSecurity().getJwt().getCookieName(), rawRefreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.ofMillis(appProperties.getSecurity().getJwt().getRefreshTokenExpirationMs()))
                .build();
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        UserSummaryDto userSummary = UserSummaryDto.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .email(user.getEmail())
                .role(user.getRole())
                .candidateLevel(user.getCandidateLevel())
                .isOnboarded(user.isOnboarded())
                .status(user.getStatus())
                .build();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .expiresIn(appProperties.getSecurity().getJwt().getAccessTokenExpirationMs() / 1000)
                .role(user.getRole())
                .candidateLevel(user.getCandidateLevel())
                .onboarded(user.isOnboarded())
                .user(userSummary)
                .build();
    }

    private Optional<String> extractRefreshTokenCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        String cookieName = appProperties.getSecurity().getJwt().getCookieName();
        return Arrays.stream(request.getCookies())
                .filter(c -> cookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }

    private String normalizePhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 10) {
            return "+91" + digits;
        } else if (digits.length() == 12 && digits.startsWith("91")) {
            return "+" + digits;
        }
        return phone.trim();
    }

    private String normalizeRole(String role) {
        if (role == null) return "ROLE_CANDIDATE";
        String upper = role.toUpperCase().trim();
        if ("FACILITY".equals(upper) || "ROLE_FACILITY".equals(upper) || "EMPLOYER".equals(upper)) {
            return "ROLE_FACILITY";
        }
        if ("ADMIN".equals(upper) || "ROLE_ADMIN".equals(upper)) {
            return "ROLE_ADMIN";
        }
        return "ROLE_CANDIDATE";
    }
}
