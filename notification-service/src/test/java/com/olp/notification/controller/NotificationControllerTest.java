package com.olp.notification.controller;

import com.olp.notification.model.NotificationLog;
import com.olp.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notificationService);
    }

    // --- getMyNotifications ---

    @Test
    void getMyNotifications_success() {
        when(notificationService.getMyNotifications(1L)).thenReturn(List.of(new NotificationLog()));
        ResponseEntity<List<NotificationLog>> response = controller.getMyNotifications(1L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void getMyNotifications_noUserId_throwsUnauthorized() {
        assertThrows(ResponseStatusException.class, () -> controller.getMyNotifications(null));
    }

    // --- getUnreadCount ---

    @Test
    void getUnreadCount_success() {
        when(notificationService.getUnreadCount(1L)).thenReturn(3);
        ResponseEntity<Map<String, Integer>> response = controller.getUnreadCount(1L);
        assertEquals(3, response.getBody().get("count"));
    }

    // --- markRead ---

    @Test
    void markRead_success() {
        NotificationLog log = new NotificationLog();
        log.setId(1L);
        log.setUserId(1L);
        log.setIsRead(false);

        when(notificationService.getNotification(1L)).thenReturn(Optional.of(log));
        when(notificationService.markRead(1L)).thenReturn(Optional.of(log));

        ResponseEntity<NotificationLog> response = controller.markRead(1L, 1L, "STUDENT");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void markRead_wrongUser_returnsNotFound() {
        NotificationLog log = new NotificationLog();
        log.setId(1L);
        log.setUserId(2L); // different user

        when(notificationService.getNotification(1L)).thenReturn(Optional.of(log));

        ResponseEntity<NotificationLog> response = controller.markRead(1L, 1L, "STUDENT");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void markRead_admin_canAccessAny() {
        NotificationLog log = new NotificationLog();
        log.setId(1L);
        log.setUserId(99L);

        when(notificationService.getNotification(1L)).thenReturn(Optional.of(log));
        when(notificationService.markRead(1L)).thenReturn(Optional.of(log));

        ResponseEntity<NotificationLog> response = controller.markRead(1L, 1L, "ADMIN");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // --- markAllRead ---

    @Test
    void markAllRead_success() {
        ResponseEntity<?> response = controller.markAllRead(1L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(notificationService).markAllRead(1L);
    }

    // --- delete ---

    @Test
    void delete_ownNotification_success() {
        NotificationLog log = new NotificationLog();
        log.setId(1L);
        log.setUserId(1L);

        when(notificationService.getNotification(1L)).thenReturn(Optional.of(log));

        ResponseEntity<?> response = controller.delete(1L, 1L, "STUDENT");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(notificationService).deleteNotification(1L);
    }

    @Test
    void delete_otherUserNotification_returnsNotFound() {
        NotificationLog log = new NotificationLog();
        log.setId(1L);
        log.setUserId(99L);

        when(notificationService.getNotification(1L)).thenReturn(Optional.of(log));

        ResponseEntity<?> response = controller.delete(1L, 1L, "STUDENT");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // --- sendToUser ---

    @Test
    void sendToUser_adminRole_success() {
        when(notificationService.createNotification(any())).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<NotificationLog> response = controller.sendToUser(5L,
                Map.of("title", "Test", "message", "Hello"), "ADMIN");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void sendToUser_nonAdmin_throwsForbidden() {
        assertThrows(ResponseStatusException.class, () ->
                controller.sendToUser(5L, Map.of("title", "Test"), "STUDENT"));
    }

    // --- broadcast ---

    @Test
    void broadcast_adminRole_success() {
        ResponseEntity<Map<String, String>> response = controller.broadcast(
                Map.of("title", "Announcement", "message", "Hi"), "ADMIN");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void broadcast_nonAdmin_throwsForbidden() {
        assertThrows(ResponseStatusException.class, () ->
                controller.broadcast(Map.of("title", "Test"), "INSTRUCTOR"));
    }
}
