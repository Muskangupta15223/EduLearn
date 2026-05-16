package com.olp.notification.service;

import com.olp.notification.dto.TargetedNotificationRequest;
import com.olp.notification.dto.TargetedNotificationResponse;
import com.olp.notification.model.NotificationLog;
import com.olp.notification.repository.NotificationLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificationService {

    private final NotificationLogRepository repository;
    private final RestTemplate restTemplate;

    public NotificationService(NotificationLogRepository repository, RestTemplate restTemplate) {
        this.repository = repository;
        this.restTemplate = restTemplate;
    }

    public List<NotificationLog> getMyNotifications(Long userId) {
        return repository.findByUserIdOrderBySentAtDesc(userId);
    }

    public int getUnreadCount(Long userId) {
        return (int) repository.countByUserIdAndIsReadFalse(userId);
    }

    public Optional<NotificationLog> markRead(Long id) {
        return repository.findById(id).map(n -> {
            n.setIsRead(true);
            return repository.save(n);
        });
    }

    public Optional<NotificationLog> getNotification(Long id) {
        return repository.findById(id);
    }

    public void markAllRead(Long userId) {
        repository.findByUserIdOrderBySentAtDesc(userId).forEach(n -> {
            n.setIsRead(true);
            repository.save(n);
        });
    }

    public void deleteNotification(Long id) {
        repository.deleteById(id);
    }

    public NotificationLog createNotification(NotificationLog log) {
        if (log.getSentAt() == null) {
            log.setSentAt(LocalDateTime.now());
        }
        if (log.getIsRead() == null) {
            log.setIsRead(false);
        }
        return repository.save(log);
    }

    public void broadcast(String title, String message, String type) {
        TargetedNotificationRequest request = new TargetedNotificationRequest();
        request.setRoles(List.of("STUDENT", "INSTRUCTOR", "ADMIN"));
        request.setTitle(title);
        request.setMessage(message);
        request.setType(type);
        sendTargetedNotifications(request);
    }

    public TargetedNotificationResponse sendTargetedNotifications(TargetedNotificationRequest request) {
        List<String> roles = request.getRoles() == null ? List.of() : request.getRoles();
        LinkedHashSet<Long> recipients = new LinkedHashSet<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            try {
                List<?> users = restTemplate.getForObject(
                        "http://user-service/users?role=" + role.trim().toUpperCase(),
                        List.class
                );
                if (users == null) {
                    continue;
                }
                for (Object userObj : users) {
                    if (userObj instanceof Map<?, ?> user) {
                        Object id = user.get("id");
                        if (id != null) {
                            recipients.add(Long.valueOf(id.toString()));
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        for (Long userId : recipients) {
            NotificationLog log = new NotificationLog();
            log.setUserId(userId);
            log.setTitle(request.getTitle());
            log.setMessage(request.getMessage());
            log.setType(request.getType() == null || request.getType().isBlank() ? "announcement" : request.getType());
            log.setIsRead(false);
            log.setSentAt(LocalDateTime.now());
            repository.save(log);
        }

        TargetedNotificationResponse response = new TargetedNotificationResponse();
        response.setRecipients(recipients.size());
        response.setRoles(roles);
        return response;
    }
}
