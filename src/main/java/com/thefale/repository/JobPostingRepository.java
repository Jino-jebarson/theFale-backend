package com.thefale.repository;

import com.thefale.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {

    List<JobPosting> findByFacilityId(UUID facilityId);
    List<JobPosting> findByFacilityIdAndDeletedAtIsNull(UUID facilityId);
    List<JobPosting> findByStatus(String status);
    long countByStatus(String status);

    @Query("SELECT j FROM JobPosting j WHERE j.status = 'OPEN' AND (:isStudent = false OR j.internship = true) AND j.deletedAt IS NULL ORDER BY j.createdAt DESC")
    List<JobPosting> findOpenPostings(@Param("isStudent") boolean isStudent);

    @Query("SELECT j FROM JobPosting j WHERE j.status = 'OPEN' AND (:isStudent = false OR j.internship = true) AND LOWER(j.city) = LOWER(:city) AND j.deletedAt IS NULL ORDER BY j.createdAt DESC")
    List<JobPosting> findOpenPostingsByCity(@Param("isStudent") boolean isStudent, @Param("city") String city);
}
