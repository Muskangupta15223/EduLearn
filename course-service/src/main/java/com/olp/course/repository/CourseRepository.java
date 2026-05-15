package com.olp.course.repository;

import com.olp.course.model.Course;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {
    @EntityGraph(attributePaths = {"modules"})
    List<Course> findAll();

    @EntityGraph(attributePaths = {"modules"})
    List<Course> findByStatus(String status);
    List<Course> findByCategoryAndStatus(String category, String status);
    List<Course> findByTitleContainingIgnoreCaseAndStatus(String title, String status);

    @EntityGraph(attributePaths = {"modules"})
    List<Course> findByInstructorId(Long instructorId);

    List<Course> findByLevelAndStatus(String level, String status);
    List<Course> findByLanguageIgnoreCaseAndStatus(String language, String status);

    @EntityGraph(attributePaths = {"modules"})
    List<Course> findByInstructorIdAndStatus(Long instructorId, String status);
}
