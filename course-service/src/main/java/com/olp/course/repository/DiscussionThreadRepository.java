package com.olp.course.repository;

import com.olp.course.model.DiscussionThread;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiscussionThreadRepository extends JpaRepository<DiscussionThread, Long> {
    List<DiscussionThread> findByCourseIdOrderByIsPinnedDescCreatedAtDesc(Long courseId);
}
