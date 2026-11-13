package com.thefale.repository;

import com.thefale.entity.HospitalBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HospitalBankAccountRepository extends JpaRepository<HospitalBankAccount, UUID> {

    Optional<HospitalBankAccount> findByFacilityId(UUID facilityId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from HospitalBankAccount b where b.facilityId = :id")
    Optional<HospitalBankAccount> findByFacilityIdWithLock(UUID id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from HospitalBankAccount b where b.id = :id")
    Optional<HospitalBankAccount> findByIdWithLock(UUID id);
}
