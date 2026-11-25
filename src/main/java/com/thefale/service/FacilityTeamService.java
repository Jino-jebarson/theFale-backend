package com.thefale.service;

import com.thefale.dto.FacilityTeamInviteDto;
import com.thefale.dto.FacilityTeamInviteRequest;
import com.thefale.entity.FacilityTeamInvite;
import com.thefale.entity.FacilityProfile;
import com.thefale.exception.BadRequestException;
import com.thefale.exception.ConflictException;
import com.thefale.exception.ResourceNotFoundException;
import com.thefale.repository.FacilityProfileRepository;
import com.thefale.repository.FacilityTeamInviteRepository;
import com.thefale.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FacilityTeamService {
    private final FacilityProfileRepository facilityProfileRepository;
    private final FacilityTeamInviteRepository inviteRepository;

    @Transactional(readOnly = true)
    public List<FacilityTeamInviteDto> list(CustomUserPrincipal principal) {
        UUID facilityId = facility(principal).getId();
        return inviteRepository.findByFacilityIdOrderByCreatedAtDesc(facilityId).stream().map(this::toDto).toList();
    }

    @Transactional
    public FacilityTeamInviteDto invite(CustomUserPrincipal principal, FacilityTeamInviteRequest request) {
        UUID facilityId = facility(principal).getId();
        String phone = request.getPhone().replaceAll("[\\s-]", "");
        if (phone.startsWith("+91")) phone = phone.substring(3);
        else if (phone.startsWith("91") && phone.length() == 12) phone = phone.substring(2);
        if (!phone.matches("[6-9]\\d{9}")) throw new BadRequestException("Enter a valid Indian mobile number.");
        String normalizedPhone = "+91" + phone;
        if (inviteRepository.existsByFacilityIdAndPhone(facilityId, normalizedPhone)) {
            throw new ConflictException("This person is already listed for your facility.");
        }
        FacilityTeamInvite saved = inviteRepository.save(FacilityTeamInvite.builder()
                .facilityId(facilityId)
                .name(request.getName().trim())
                .phone(normalizedPhone)
                .status("INVITED")
                .build());
        return toDto(saved);
    }

    @Transactional
    public void remove(CustomUserPrincipal principal, UUID inviteId) {
        UUID facilityId = facility(principal).getId();
        FacilityTeamInvite invite = inviteRepository.findByIdAndFacilityId(inviteId, facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Team invitation not found."));
        inviteRepository.delete(invite);
    }

    private FacilityProfile facility(CustomUserPrincipal principal) {
        if (principal == null || principal.getId() == null) throw new BadRequestException("Facility session is required.");
        return facilityProfileRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new BadRequestException("Complete facility onboarding before managing team contacts."));
    }

    private FacilityTeamInviteDto toDto(FacilityTeamInvite invite) {
        return FacilityTeamInviteDto.builder().id(invite.getId()).name(invite.getName()).phone(invite.getPhone())
                .status(invite.getStatus()).createdAt(invite.getCreatedAt()).build();
    }
}
