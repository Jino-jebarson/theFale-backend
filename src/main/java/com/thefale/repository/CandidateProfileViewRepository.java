package com.thefale.repository;

import com.thefale.entity.CandidateProfileView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface CandidateProfileViewRepository extends JpaRepository<CandidateProfileView, Long> {

    long countByCandidateId(UUID candidateId);

    long countByCandidateIdAndViewedAtGreaterThanEqual(UUID candidateId, OffsetDateTime since);

    @Query("SELECT COUNT(DISTINCT v.facilityName) FROM CandidateProfileView v WHERE v.candidateId = :candidateId AND v.viewedAt >= :since")
    long countDistinctFacilitiesByCandidateIdAndViewedAtGreaterThanEqual(
            @Param("candidateId") UUID candidateId,
            @Param("since") OffsetDateTime since
    );

    long countByCandidateIdAndViewedAtBetween(UUID candidateId, OffsetDateTime start, OffsetDateTime end);
}

