package com.thefale.repository;

import com.thefale.entity.HuddleAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HuddleAnswerRepository extends JpaRepository<HuddleAnswer, UUID> {

    List<HuddleAnswer> findByQuestionIdOrderByCreatedAtAsc(UUID questionId);
}
