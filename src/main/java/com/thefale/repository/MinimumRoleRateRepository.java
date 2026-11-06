package com.thefale.repository;

import com.thefale.entity.MinimumRoleRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MinimumRoleRateRepository extends JpaRepository<MinimumRoleRate, UUID> {

    Optional<MinimumRoleRate> findByProfessionIgnoreCase(String profession);
}
