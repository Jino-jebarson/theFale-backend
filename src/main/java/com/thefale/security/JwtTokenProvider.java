package com.thefale.security;

import com.thefale.config.AppProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final AppProperties appProperties;
    private SecretKey key;
    private static final String ISSUER = "thefale.com";
    private static final String AUDIENCE = "thefale-app";

    @PostConstruct
    public void init() {
        String secret = appProperties.getSecurity().getJwt().getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT_SECRET must be configured and at least 256 bits (32 characters).");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(CustomUserPrincipal principal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + appProperties.getSecurity().getJwt().getAccessTokenExpirationMs());

        return Jwts.builder()
                .subject(principal.getId().toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("role", principal.getRole())
                .claim("candidateLevel", principal.getCandidateLevel())
                .claim("phone", principal.getPhone())
                .claim("email", principal.getEmail())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .requireAudience(AUDIENCE)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.warn("Invalid JWT signature or format: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("JWT parsing error: {}", e.getMessage());
        }
        return false;
    }

    public CustomUserPrincipal getPrincipalFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        UUID userId = UUID.fromString(claims.getSubject());
        String role = claims.get("role", String.class);
        String candidateLevel = claims.get("candidateLevel", String.class);
        String phone = claims.get("phone", String.class);
        String email = claims.get("email", String.class);

        return CustomUserPrincipal.builder()
                .id(userId)
                .role(role)
                .candidateLevel(candidateLevel)
                .phone(phone)
                .email(email)
                .enabled(true)
                .build();
    }
}
