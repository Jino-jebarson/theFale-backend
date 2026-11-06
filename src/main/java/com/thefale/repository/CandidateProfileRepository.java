package com.thefale.repository;

import com.thefale.entity.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, UUID> {
    Optional<CandidateProfile> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
    java.util.List<CandidateProfile> findByKycStatus(String kycStatus);
    long countByKycStatus(String kycStatus);
}
