package com.thefale.security;

import com.thefale.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getSecurity().getJwt().setSecret("super-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256");
        appProperties.getSecurity().getJwt().setAccessTokenExpirationMs(60000); // 1 min

        tokenProvider = new JwtTokenProvider(appProperties);
        tokenProvider.init();
    }

    @Test
    @DisplayName("Should generate valid JWT token with accurate claims")
    void testGenerateAndValidateToken() {
        UUID userId = UUID.randomUUID();
        CustomUserPrincipal principal = CustomUserPrincipal.builder()
                .id(userId)
                .role("ROLE_CANDIDATE")
                .candidateLevel("STUDENT")
                .phone("9876543210")
                .email("student@college.edu")
                .enabled(true)
                .build();

        String token = tokenProvider.generateAccessToken(principal);
        assertThat(token).isNotNull().isNotBlank();
        assertThat(tokenProvider.validateToken(token)).isTrue();

        CustomUserPrincipal parsed = tokenProvider.getPrincipalFromToken(token);
        assertThat(parsed.getId()).isEqualTo(userId);
        assertThat(parsed.getRole()).isEqualTo("ROLE_CANDIDATE");
        assertThat(parsed.getCandidateLevel()).isEqualTo("STUDENT");
        assertThat(parsed.isStudent()).isTrue();
    }

    @Test
    @DisplayName("Should reject expired JWT token")
    void testExpiredToken() {
        appProperties.getSecurity().getJwt().setAccessTokenExpirationMs(-1000); // Expired 1 second ago
        JwtTokenProvider expiredProvider = new JwtTokenProvider(appProperties);
        expiredProvider.init();

        CustomUserPrincipal principal = CustomUserPrincipal.builder()
                .id(UUID.randomUUID())
                .role("ROLE_CANDIDATE")
                .candidateLevel("PROFESSIONAL")
                .enabled(true)
                .build();

        String token = expiredProvider.generateAccessToken(principal);
        assertThat(expiredProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("Should reject tampered JWT token")
    void testTamperedToken() {
        CustomUserPrincipal principal = CustomUserPrincipal.builder()
                .id(UUID.randomUUID())
                .role("ROLE_CANDIDATE")
                .candidateLevel("PROFESSIONAL")
                .enabled(true)
                .build();

        String token = tokenProvider.generateAccessToken(principal);
        String tampered = token.substring(0, token.length() - 5) + "abcde";

        assertThat(tokenProvider.validateToken(tampered)).isFalse();
    }
}
