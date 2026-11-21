package com.thefale.service;

import com.thefale.entity.FacilityProfile;
import com.thefale.dto.FacilityOnboardingRequest;
import com.thefale.exception.BadRequestException;
import com.thefale.exception.ForbiddenException;
import com.thefale.repository.CandidateProfileRepository;
import com.thefale.repository.FacilityProfileRepository;
import com.thefale.security.CustomUserPrincipal;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FacilityVerificationGateTest {
    @Test
    void onboardingCannotSkipAgreement() {
        FacilityProfileRepository profiles = mock(FacilityProfileRepository.class);
        CustomUserPrincipal principal = CustomUserPrincipal.builder().id(UUID.randomUUID()).build();
        when(profiles.findByUserId(principal.getId())).thenReturn(Optional.empty());
        FacilityService service = new FacilityService(profiles, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.onboard(principal, FacilityOnboardingRequest.builder().build()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("agreement");
        verify(profiles, never()).save(any());
    }

    @Test
    void pendingFacilityCannotSearchOrUnlockCandidates() {
        FacilityService facilities = mock(FacilityService.class);
        CandidateProfileRepository candidates = mock(CandidateProfileRepository.class);
        CustomUserPrincipal principal = CustomUserPrincipal.builder().id(UUID.randomUUID()).build();
        when(facilities.getOrCreateFacilityProfile(principal)).thenReturn(FacilityProfile.builder().kycStatus("PENDING").build());
        FacilityResdexService service = new FacilityResdexService(null, facilities, candidates, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.searchCandidates(principal, null, null, null))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("verification");
        assertThatThrownBy(() -> service.unlockCandidate(principal, UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("verification");
        verifyNoInteractions(candidates);
    }
}
