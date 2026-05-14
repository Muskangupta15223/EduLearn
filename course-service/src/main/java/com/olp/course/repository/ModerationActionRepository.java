package com.olp.course.repository;

import com.olp.course.model.ModerationAction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {
    List<ModerationAction> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);
}
