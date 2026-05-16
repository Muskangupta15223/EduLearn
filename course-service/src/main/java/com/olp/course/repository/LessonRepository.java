package com.olp.course.repository;

import com.olp.course.model.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findByModuleIdOrderByLessonOrderAsc(Long moduleId);
    List<Lesson> findByModuleIdInOrderByModuleIdAscLessonOrderAsc(List<Long> moduleIds);
    List<Lesson> findByModuleCourseIdAndIsFreePreviewTrueOrderByLessonOrderAsc(Long courseId);
}
