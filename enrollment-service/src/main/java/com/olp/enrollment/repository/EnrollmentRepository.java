package com.olp.enrollment.repository;

import com.olp.enrollment.model.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByUserId(Long userId);
    List<Enrollment> findByCourseId(Long courseId);
    List<Enrollment> findByCourseIdIn(List<Long> courseIds);
    Optional<Enrollment> findByUserIdAndCourseId(Long userId, Long courseId);
    long countByCourseId(Long courseId);
    boolean existsByUserIdAndCourseId(Long userId, Long courseId);
}
