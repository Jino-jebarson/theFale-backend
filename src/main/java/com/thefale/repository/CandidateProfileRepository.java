package com.thefale.repository;

import com.thefale.entity.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT c FROM CandidateProfile c WHERE c.id = :id")
    java.util.Optional<CandidateProfile> findByIdWithLock(@org.springframework.data.repository.query.Param("id") java.util.UUID id);

    Optional<CandidateProfile> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
    java.util.List<CandidateProfile> findByKycStatus(String kycStatus);
    long countByKycStatus(String kycStatus);
}
