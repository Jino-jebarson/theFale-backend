package com.thefale.repository;

import com.thefale.entity.LocumShift;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocumShiftRepository extends JpaRepository<LocumShift, UUID> {

    List<LocumShift> findByFacilityIdOrderByStartAtDesc(UUID facilityId);

    @Query("SELECT s FROM LocumShift s WHERE s.status = 'OPEN' AND s.endAt > CURRENT_TIMESTAMP ORDER BY s.startAt ASC")
    List<LocumShift> findOpenShifts();

    @Query("SELECT s FROM LocumShift s WHERE s.status IN ('PENDING_APPROVAL', 'OPEN') AND s.endAt <= :now")
    List<LocumShift> findExpiredUnfilledShifts(@Param("now") java.time.Instant now);

    @Query("SELECT COUNT(s) FROM LocumShift s WHERE s.facilityId = :facilityId AND s.status IN ('OPEN', 'FILLED', 'BOOKED', 'COMPLETED', 'EXPIRED')")
    long countApprovedShiftsByFacility(@Param("facilityId") UUID facilityId);

    @Query("SELECT COUNT(s) FROM LocumShift s WHERE s.facilityId = :facilityId AND s.status NOT IN ('CANCELLED', 'REJECTED')")
    long countValidFeeShiftsByFacility(@Param("facilityId") UUID facilityId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM LocumShift s WHERE s.id = :id")
    Optional<LocumShift> findByIdWithLock(@Param("id") UUID id);

    List<LocumShift> findByStatus(String status);
    long countByStatus(String status);
}
