package com.olp.notification.controller;

import com.olp.notification.dto.NotificationDtos;
import com.olp.notification.dto.NotificationDtos.BroadcastRequest;
import com.olp.notification.dto.NotificationDtos.NotificationCreateRequest;
import com.olp.notification.dto.NotificationDtos.NotificationResponse;
import com.olp.notification.dto.TargetedNotificationRequest;
import com.olp.notification.dto.TargetedNotificationResponse;
import com.olp.notification.model.NotificationLog;
import com.olp.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/me")
    public ResponseEntity<List<NotificationResponse>> getMyNotifications(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUserId(userId);
        return ResponseEntity.ok(NotificationDtos.toResponses(notificationService.getMyNotifications(userId)));
    }

    @GetMapping("/me/unread-count")
    public ResponseEntity<Map<String, Integer>> getUnreadCount(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUserId(userId);
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(userId)));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireUserId(userId);
        NotificationLog existing = notificationService.getNotification(id).orElse(null);
        if (existing == null || !canAccess(existing, userId, role)) {
            return ResponseEntity.notFound().build();
        }
        return notificationService.markRead(id)
                .map(NotificationResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/me/read-all")
    public ResponseEntity<Void> markAllRead(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUserId(userId);
        notificationService.markAllRead(userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireUserId(userId);
        NotificationLog existing = notificationService.getNotification(id).orElse(null);
        if (existing == null || !canAccess(existing, userId, role)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        notificationService.deleteNotification(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/send/{userId}")
    public ResponseEntity<NotificationResponse> sendToUser(
            @PathVariable Long userId,
            @RequestBody NotificationCreateRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        NotificationLog log = new NotificationLog();
        log.setUserId(userId);
        log.setTitle(request.title() == null || request.title().isBlank() ? "System Notification" : request.title());
        log.setMessage(request.message() == null ? "" : request.message());
        log.setType(request.type() == null || request.type().isBlank() ? "system" : request.type());
        log.setIsRead(false);
        log.setSentAt(LocalDateTime.now());
        return ResponseEntity.ok(NotificationResponse.from(notificationService.createNotification(log)));
    }

    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, String>> broadcast(
            @RequestBody BroadcastRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        notificationService.broadcast(
                request.title() == null || request.title().isBlank() ? "Announcement" : request.title(),
                request.message() == null ? "" : request.message(),
                request.type() == null || request.type().isBlank() ? "announcement" : request.type()
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
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user id");
        }
    }

    private void requireAdmin(String role) {
        if (role == null || !"ADMIN".equalsIgnoreCase(role)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    private boolean canAccess(NotificationLog log, Long userId, String role) {
        return "ADMIN".equalsIgnoreCase(role) || (log.getUserId() != null && log.getUserId().equals(userId));
    }
}
