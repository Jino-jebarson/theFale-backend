package com.thefale.security;

import com.thefale.config.AppProperties;
import com.thefale.entity.OtpVerification;
import com.thefale.entity.User;
import com.thefale.exception.BadRequestException;
import com.thefale.repository.OtpVerificationRepository;
import com.thefale.repository.UserRepository;
import com.thefale.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FacilityAgreementCodeTest {
    @Test
    void checksCodeAgainstSignedInFacilityAndConsumesIt() {
        UUID id = UUID.randomUUID();
        UserRepository users = mock(UserRepository.class);
        OtpVerificationRepository otps = mock(OtpVerificationRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User user = User.builder().id(id).role("ROLE_FACILITY").phone("+919876543210").build();
        OtpVerification otp = OtpVerification.builder().contact(user.getPhone()).otpHash(encoder.encode("123456"))
                .expiresAt(Instant.now().plusSeconds(300)).build();
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(otps.findTopByContactAndVerifiedFalseOrderByCreatedAtDesc(user.getPhone())).thenReturn(Optional.of(otp));
        AuthService service = new AuthService(users, otps, null, null, encoder, new AppProperties(), null);
        CustomUserPrincipal principal = CustomUserPrincipal.builder().id(id).role("ROLE_FACILITY").build();

        assertThatThrownBy(() -> service.verifyFacilityAgreementCode(principal, "000000"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Invalid signing code");
        assertThat(otp.isVerified()).isFalse();
        assertThat(otp.getAttempts()).isEqualTo(1);
        service.verifyFacilityAgreementCode(principal, "123456");
        assertThat(otp.isVerified()).isTrue();
        assertThat(otp.getAttempts()).isEqualTo(2);
        verify(otps, times(2)).save(otp);
    }

    @Test
    void rejectsExpiredCode() {
        UUID id = UUID.randomUUID();
        UserRepository users = mock(UserRepository.class);
        OtpVerificationRepository otps = mock(OtpVerificationRepository.class);
        User user = User.builder().id(id).role("ROLE_FACILITY").phone("+919876543210").build();
        OtpVerification otp = OtpVerification.builder().contact(user.getPhone()).otpHash("hash")
                .expiresAt(Instant.now().minusSeconds(1)).build();
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(otps.findTopByContactAndVerifiedFalseOrderByCreatedAtDesc(user.getPhone())).thenReturn(Optional.of(otp));
        AuthService service = new AuthService(users, otps, null, null, new BCryptPasswordEncoder(), new AppProperties(), null);
        assertThatThrownBy(() -> service.verifyFacilityAgreementCode(CustomUserPrincipal.builder().id(id).build(), "123456"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("expired");
        verify(otps, never()).save(any());
    }
}
