package com.olp.notification.controller;

import com.olp.notification.dto.TargetedNotificationRequest;
import com.olp.notification.dto.TargetedNotificationResponse;
import com.olp.notification.model.NotificationLog;
import com.olp.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/me")
    public ResponseEntity<List<NotificationLog>> getMyNotifications(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUserId(userId);
        return ResponseEntity.ok(notificationService.getMyNotifications(userId));
    }

    @GetMapping("/me/unread-count")
    public ResponseEntity<Map<String, Integer>> getUnreadCount(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUserId(userId);
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(userId)));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationLog> markRead(@PathVariable Long id,
                                                    @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                    @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireUserId(userId);
        NotificationLog existing = notificationService.getNotification(id).orElse(null);
        if (existing == null || !canAccess(existing, userId, role)) {
            return ResponseEntity.notFound().build();
        }
        return notificationService.markRead(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/me/read-all")
    public ResponseEntity<?> markAllRead(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUserId(userId);
        notificationService.markAllRead(userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id,
                                    @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                    @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireUserId(userId);
        NotificationLog existing = notificationService.getNotification(id).orElse(null);
        if (existing == null || !canAccess(existing, userId, role)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).build();
        }
        notificationService.deleteNotification(id);
        return ResponseEntity.ok().build();
    }

    // Send notification to a specific user (admin/system)
    @PostMapping("/send/{userId}")
    public ResponseEntity<NotificationLog> sendToUser(@PathVariable Long userId,
                                                       @RequestBody Map<String, String> body,
                                                       @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        NotificationLog log = new NotificationLog();
        log.setUserId(userId);
        log.setTitle(body.getOrDefault("title", "System Notification"));
        log.setMessage(body.getOrDefault("message", ""));
        log.setType(body.getOrDefault("type", "system"));
        log.setIsRead(false);
        log.setSentAt(LocalDateTime.now());
        return ResponseEntity.ok(notificationService.createNotification(log));
    }

    // Broadcast notification to all users (admin)
    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, String>> broadcast(@RequestBody Map<String, String> body,
                                                         @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        notificationService.broadcast(
                body.getOrDefault("title", "Announcement"),
                body.getOrDefault("message", ""),
                body.getOrDefault("type", "announcement")
        );
        return ResponseEntity.ok(Map.of("status", "broadcast sent"));
    }

    @PostMapping("/targeted")
    public ResponseEntity<TargetedNotificationResponse> sendTargeted(
            @RequestBody TargetedNotificationRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return ResponseEntity.ok(notificationService.sendTargetedNotifications(request));
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Missing user id");
        }
    }

    private void requireAdmin(String role) {
        if (role == null || !"ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    private boolean canAccess(NotificationLog log, Long userId, String role) {
        return "ADMIN".equalsIgnoreCase(role) || (log.getUserId() != null && log.getUserId().equals(userId));
    }
}
