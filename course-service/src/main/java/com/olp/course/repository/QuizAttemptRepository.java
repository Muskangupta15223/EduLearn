package com.olp.course.repository;

import com.olp.course.model.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    List<QuizAttempt> findByQuizIdAndUserId(Long quizId, Long userId);
    long countByQuizIdAndUserId(Long quizId, Long userId);
}
