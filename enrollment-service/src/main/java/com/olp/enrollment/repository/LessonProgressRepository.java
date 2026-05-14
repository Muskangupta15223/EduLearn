package com.olp.enrollment.repository;

import com.olp.enrollment.model.LessonProgress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {
    Optional<LessonProgress> findByUserIdAndCourseIdAndLessonId(Long userId, Long courseId, Long lessonId);
    List<LessonProgress> findByUserIdAndCourseIdOrderByLessonIdAsc(Long userId, Long courseId);
    long countByUserIdAndCourseIdAndStatus(Long userId, Long courseId, String status);
}
