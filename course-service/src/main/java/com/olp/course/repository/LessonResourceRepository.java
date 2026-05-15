package com.olp.course.repository;

import com.olp.course.model.LessonResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LessonResourceRepository extends JpaRepository<LessonResource, Long> {
    List<LessonResource> findByLessonIdOrderByDisplayOrderAsc(Long lessonId);
    List<LessonResource> findByLessonIdInOrderByLessonIdAscDisplayOrderAsc(List<Long> lessonIds);
    void deleteByLessonId(Long lessonId);
}
