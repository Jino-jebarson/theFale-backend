package com.thefale.repository;

import com.thefale.entity.FacilityProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FacilityProfileRepository extends JpaRepository<FacilityProfile, UUID> {
    Optional<FacilityProfile> findByUserId(UUID userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FacilityProfile f where f.id = :id")
    Optional<FacilityProfile> findByIdWithLock(@Param("id") UUID id);
    boolean existsByUserId(UUID userId);
    java.util.List<FacilityProfile> findByKycStatus(String kycStatus);
    long countByKycStatus(String kycStatus);
}
