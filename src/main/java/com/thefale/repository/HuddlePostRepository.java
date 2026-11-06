package com.thefale.repository;

import com.thefale.entity.HuddlePost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HuddlePostRepository extends JpaRepository<HuddlePost, UUID> {

    @Query("SELECT p FROM HuddlePost p WHERE p.status = 'LIVE' AND (:field = 'All' OR LOWER(p.field) = LOWER(:field)) AND p.deletedAt IS NULL ORDER BY p.isPinned DESC, p.createdAt DESC")
    List<HuddlePost> findLivePostsByField(@Param("field") String field);

    @Query("SELECT p FROM HuddlePost p WHERE (p.status = 'FLAGGED' OR p.flagCount > 0) AND p.status != 'REMOVED' AND p.deletedAt IS NULL ORDER BY p.flagCount DESC, p.createdAt DESC")
    List<HuddlePost> findFlaggedPosts();

    @Query("SELECT p FROM HuddlePost p WHERE p.status = 'REVIEW' AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<HuddlePost> findReviewPosts();
}
