package com.thefale.repository;

import com.thefale.entity.HospitalBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HospitalBankAccountRepository extends JpaRepository<HospitalBankAccount, UUID> {

    Optional<HospitalBankAccount> findByFacilityId(UUID facilityId);
}
