package com.thefale.repository;

import com.thefale.entity.HuddleComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HuddleCommentRepository extends JpaRepository<HuddleComment, UUID> {

    List<HuddleComment> findByPostIdOrderByCreatedAtAsc(UUID postId);

    int countByPostId(UUID postId);
}
