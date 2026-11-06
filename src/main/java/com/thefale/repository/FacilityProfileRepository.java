package com.thefale.repository;

import com.thefale.entity.FacilityProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FacilityProfileRepository extends JpaRepository<FacilityProfile, UUID> {
    Optional<FacilityProfile> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
    java.util.List<FacilityProfile> findByKycStatus(String kycStatus);
    long countByKycStatus(String kycStatus);
}
