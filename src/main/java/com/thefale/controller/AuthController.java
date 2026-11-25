package com.thefale.controller;

import com.thefale.dto.*;
import com.thefale.security.CustomUserPrincipal;
import com.thefale.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Passwordless OTP mobile login, token refresh, and admin authentication")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/otp/send")
    @Operation(summary = "Send OTP code to Indian mobile number")
    public ResponseEntity<ApiResponse<OtpSendResponse>> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        OtpSendResponse response = authService.sendOtp(request);
        return ResponseEntity.ok(ApiResponse.success("OTP sent successfully", response));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify OTP code and authenticate mobile user")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.verifyOtp(request, response);
        return ResponseEntity.ok(ApiResponse.success("Authentication successful", authResponse));
    }

    @PostMapping("/facility/login")
    @Operation(summary = "Sign in to a facility account with mobile number and password")
    public ResponseEntity<ApiResponse<AuthResponse>> facilityPasswordLogin(
            @Valid @RequestBody FacilityPasswordLoginRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.facilityPasswordLogin(request, response);
        return ResponseEntity.ok(ApiResponse.success("Facility sign-in successful", authResponse));
    }

    @PutMapping("/facility/password")
    @Operation(summary = "Set or update the authenticated facility account password")
    public ResponseEntity<ApiResponse<Void>> updateFacilityPassword(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody FacilityPasswordUpdateRequest request) {
        authService.updateFacilityPassword(principal, request);
        return ResponseEntity.ok(ApiResponse.success("Facility password updated successfully"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and issue a fresh access token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.refreshToken(request, response);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", authResponse));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke active session and clear refresh token cookie")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authService.logout(request, response);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile from database")
    public ResponseEntity<ApiResponse<UserSummaryDto>> getCurrentUser(
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        UserSummaryDto userSummary = authService.getCurrentUserSummary(principal);
        return ResponseEntity.ok(ApiResponse.success(userSummary));
    }

    @PatchMapping("/me/language")
    @Operation(summary = "Save the authenticated user's preferred interface language")
    public ResponseEntity<ApiResponse<UserSummaryDto>> updatePreferredLanguage(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody PreferredLanguageUpdateRequest request
    ) {
        UserSummaryDto userSummary = authService.updatePreferredLanguage(principal, request);
        return ResponseEntity.ok(ApiResponse.success(userSummary));
    }

    @PostMapping("/admin/login")
    @Operation(summary = "Administrator credentials login")
    public ResponseEntity<ApiResponse<AuthResponse>> adminLogin(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.adminLogin(request, response);
        return ResponseEntity.ok(ApiResponse.success("Admin login successful", authResponse));
    }

    @PostMapping("/admin/signup")
    @Operation(summary = "Register a new platform administrator with secured passcode")
    public ResponseEntity<ApiResponse<AuthResponse>> adminSignup(
            @Valid @RequestBody AdminSignupRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.adminSignup(request, response);
        return ResponseEntity.ok(ApiResponse.success("Administrator account registered successfully", authResponse));
    }

    @PostMapping("/admin/forgot-password")
    @Operation(summary = "Reset administrator password with secured passcode")
    public ResponseEntity<ApiResponse<Void>> adminForgotPassword(
            @Valid @RequestBody AdminForgotPasswordRequest request
    ) {
        authService.adminForgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully. You can now sign in with your new credentials."));
    }
}
