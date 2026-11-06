package com.thefale.repository;

import com.thefale.entity.FacilityWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FacilityWalletRepository extends JpaRepository<FacilityWallet, UUID> {

    Optional<FacilityWallet> findByFacilityId(UUID facilityId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM FacilityWallet w WHERE w.facilityId = :facilityId")
    Optional<FacilityWallet> findByFacilityIdWithLock(@Param("facilityId") UUID facilityId);
}
