package com.thefale.repository;

import com.thefale.entity.HuddleFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HuddleFlagRepository extends JpaRepository<HuddleFlag, UUID> {

    List<HuddleFlag> findByResolvedFalseOrderByCreatedAtDesc();

    boolean existsByPostIdAndReportedBy(UUID postId, UUID reportedBy);
}
