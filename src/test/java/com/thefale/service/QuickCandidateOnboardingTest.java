package com.thefale.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thefale.dto.CandidateOnboardingRequest;
import com.thefale.dto.CandidateProfileDto;
import com.thefale.entity.CandidateProfile;
import com.thefale.entity.User;
import com.thefale.repository.CandidateProfileRepository;
import com.thefale.repository.CandidateDocumentRepository;
import com.thefale.repository.UserRepository;
import com.thefale.security.CustomUserPrincipal;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuickCandidateOnboardingTest {
    @Test
    void explicitGraduateLevelIsNotReclassifiedWhenCourseExistsWithoutRegistration() {
        UUID id = UUID.randomUUID();
        UserRepository users = mock(UserRepository.class);
        CandidateProfileRepository profiles = mock(CandidateProfileRepository.class);
        CandidateDocumentRepository documents = mock(CandidateDocumentRepository.class);
        User user = User.builder().id(id).role("ROLE_CANDIDATE").build();
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(profiles.findByUserId(id)).thenReturn(Optional.empty());
        when(documents.findByCandidateId(any())).thenReturn(List.of());
        when(profiles.save(any(CandidateProfile.class))).thenAnswer(i -> { CandidateProfile p = i.getArgument(0); p.setId(UUID.randomUUID()); return p; });
        CandidateService service = spy(new CandidateService(users, profiles, documents, null, null, null, null,
                mock(AuditLogService.class), new ObjectMapper(), mock(ReferralService.class)));
        CustomUserPrincipal principal = CustomUserPrincipal.builder().id(id).build();
        doReturn(CandidateProfileDto.builder().build()).when(service).getCandidateProfile(principal);
        service.onboardCandidate(principal, CandidateOnboardingRequest.builder().fullName("Test Candidate")
                .city("Jayanagar").candidateLevel("FRESHER").course("BSc")
                .profession("Medical Coder").build());
        assertThat(user.getCandidateLevel()).isEqualTo("FRESHER");
    }

    @Test
    void studentQuickSetupDoesNotRequireCollegeYet() {
        UUID id = UUID.randomUUID();
        UserRepository users = mock(UserRepository.class);
        CandidateProfileRepository profiles = mock(CandidateProfileRepository.class);
        CandidateDocumentRepository documents = mock(CandidateDocumentRepository.class);
        User user = User.builder().id(id).role("ROLE_CANDIDATE").build();
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(profiles.findByUserId(id)).thenReturn(Optional.empty());
        when(documents.findByCandidateId(any())).thenReturn(List.of());
        when(profiles.save(any(CandidateProfile.class))).thenAnswer(i -> { CandidateProfile p = i.getArgument(0); p.setId(UUID.randomUUID()); return p; });
        CandidateService service = spy(new CandidateService(users, profiles, documents, null, null, null, null,
                mock(AuditLogService.class), new ObjectMapper(), mock(ReferralService.class)));
        CustomUserPrincipal principal = CustomUserPrincipal.builder().id(id).build();
        doReturn(CandidateProfileDto.builder().build()).when(service).getCandidateProfile(principal);
        service.onboardCandidate(principal, CandidateOnboardingRequest.builder().fullName("Test Student")
                .city("Jayanagar").candidateLevel("STUDENT").course("B.Sc Nursing").profession("Nurse").build());
        assertThat(user.getCandidateLevel()).isEqualTo("STUDENT");
        verify(profiles).save(argThat(p -> "Nurse".equals(p.getProfession()) && p.getCollege() == null));
    }
}
