package com.thefale.repository;

import com.thefale.entity.FacilityTeamInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityTeamInviteRepository extends JpaRepository<FacilityTeamInvite, UUID> {
    List<FacilityTeamInvite> findByFacilityIdOrderByCreatedAtDesc(UUID facilityId);
    boolean existsByFacilityIdAndPhone(UUID facilityId, String phone);
    Optional<FacilityTeamInvite> findByIdAndFacilityId(UUID id, UUID facilityId);
}
