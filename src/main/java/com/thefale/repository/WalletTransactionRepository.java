package com.thefale.repository;

import com.thefale.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId);

    boolean existsByUtr(String utr);

    Optional<WalletTransaction> findByUtr(String utr);

    Optional<WalletTransaction> findByRefNumber(String refNumber);
    Optional<WalletTransaction> findByClaimIdAndType(UUID claimId, String type);
    List<WalletTransaction> findByTypeAndStatusOrderByCreatedAtAsc(String type, String status);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from WalletTransaction t where t.id = :id")
    Optional<WalletTransaction> findByIdWithLock(UUID id);
}
