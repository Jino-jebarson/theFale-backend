package com.thefale.security;

import com.thefale.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<CustomUserPrincipal> getCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static CustomUserPrincipal getRequiredPrincipal() {
        return getCurrentPrincipal()
                .orElseThrow(() -> new UnauthorizedException("User session is not authenticated."));
    }

    public static UUID getCurrentUserId() {
        return getRequiredPrincipal().getId();
    }

    public static boolean isStudent() {
        return getCurrentPrincipal().map(CustomUserPrincipal::isStudent).orElse(false);
    }

    public static boolean isGraduate() {
        return getCurrentPrincipal().map(CustomUserPrincipal::isGraduate).orElse(false);
    }

    public static boolean isFacility() {
        return getCurrentPrincipal().map(CustomUserPrincipal::isFacility).orElse(false);
    }

    public static boolean isAdmin() {
        return getCurrentPrincipal().map(CustomUserPrincipal::isAdmin).orElse(false);
    }
}
