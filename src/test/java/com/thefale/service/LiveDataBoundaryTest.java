package com.thefale.service;

import com.thefale.dto.CandidatePayoutMethodDto;
import com.thefale.dto.UpdateHospitalBankRequest;
import com.thefale.entity.CandidatePayoutMethod;
import com.thefale.entity.CandidateProfile;
import com.thefale.entity.FacilityProfile;
import com.thefale.entity.HospitalBankAccount;
import com.thefale.exception.BadRequestException;
import com.thefale.repository.CandidatePayoutMethodRepository;
import com.thefale.repository.CandidateProfileRepository;
import com.thefale.repository.FacilityProfileRepository;
import com.thefale.repository.HospitalBankAccountRepository;
import com.thefale.security.CustomUserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveDataBoundaryTest {
    @Mock CandidateProfileRepository candidateProfiles;
    @Mock CandidatePayoutMethodRepository payoutMethods;
    @Mock FacilityProfileRepository facilityProfiles;
    @Mock HospitalBankAccountRepository bankAccounts;
    @Mock AuditLogService auditLog;
    @InjectMocks CandidateService candidateService;
    @InjectMocks FacilityService facilityService;

    private final UUID userId = UUID.randomUUID();
    private final CustomUserPrincipal principal = CustomUserPrincipal.builder().id(userId).build();

    @Test
    void missingFacilityProfileRequiresOnboardingWithoutCreatingPlaceholderRecords() {
        when(facilityProfiles.findByUserId(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> facilityService.getOrCreateFacilityProfile(principal))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("onboarding");
        verify(facilityProfiles, never()).save(any());
    }

    @Test
    void savingUpiDetailsDoesNotVerifyOwnership() {
        UUID candidateId = UUID.randomUUID();
        when(candidateProfiles.findByUserId(userId)).thenReturn(Optional.of(CandidateProfile.builder().id(candidateId).build()));
        when(payoutMethods.findByCandidateId(candidateId)).thenReturn(Optional.empty());
        when(payoutMethods.save(any(CandidatePayoutMethod.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var result = candidateService.savePayoutMethod(principal, CandidatePayoutMethodDto.builder()
                .methodType("upi").upiId("person@bank").accountHolderName("Account Holder").build());
        assertThat(result.getStatus()).isEqualTo("Pending");
        assertThat(result.getVerifiedAt()).isNull();
    }

    @Test
    void savingHospitalBankDetailsDoesNotFabricatePennyDropConfirmation() {
        UUID facilityId = UUID.randomUUID();
        when(facilityProfiles.findByUserId(userId)).thenReturn(Optional.of(FacilityProfile.builder().id(facilityId).build()));
        when(bankAccounts.findByFacilityId(facilityId)).thenReturn(Optional.empty());
        when(bankAccounts.save(any(HospitalBankAccount.class))).thenAnswer(invocation -> {
            HospitalBankAccount account = invocation.getArgument(0);
            account.setId(UUID.randomUUID());
            return account;
        });
        var result = facilityService.updateBankAccount(principal, UpdateHospitalBankRequest.builder()
                .bankName("Bank").accountHolderName("Facility").accountNumber("12345678901").ifsc("ABCD0123456").build());
        assertThat(result.getStatus()).isEqualTo("Under Review");
        assertThat(result.getVerifiedAt()).isNull();
    }
}
