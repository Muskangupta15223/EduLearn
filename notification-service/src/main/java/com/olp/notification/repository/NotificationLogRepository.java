package com.olp.notification.repository;

import com.olp.notification.model.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    java.util.List<NotificationLog> findByUserIdOrderBySentAtDesc(Long userId);
    long countByUserIdAndIsReadFalse(Long userId);
}
