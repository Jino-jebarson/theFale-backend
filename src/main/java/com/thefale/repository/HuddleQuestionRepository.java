package com.thefale.repository;

import com.thefale.entity.HuddleQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HuddleQuestionRepository extends JpaRepository<HuddleQuestion, UUID> {

    List<HuddleQuestion> findAllByOrderByCreatedAtDesc();
}
