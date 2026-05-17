package com.olp.notification.service;

import com.olp.notification.model.NotificationLog;
import com.olp.notification.repository.NotificationLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @SuppressWarnings("unchecked")
    private static ParameterizedTypeReference<List<?>> anyNotificationUserListType() {
        return any(ParameterizedTypeReference.class);
    }

    @Mock
    private NotificationLogRepository repository;

    @Mock
    private RestTemplate restTemplate;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(repository, restTemplate);
    }

    @Test
    void getMyNotifications_returnsList() {
        when(repository.findByUserIdOrderBySentAtDesc(1L)).thenReturn(List.of(new NotificationLog()));
        assertEquals(1, notificationService.getMyNotifications(1L).size());
    }

    @Test
    void getUnreadCount_returnsCount() {
        when(repository.countByUserIdAndIsReadFalse(1L)).thenReturn(5L);
        assertEquals(5, notificationService.getUnreadCount(1L));
    }

    @Test
    void markRead_existing_setsReadTrue() {
        NotificationLog log = new NotificationLog();
        log.setId(1L);
        log.setIsRead(false);
        when(repository.findById(1L)).thenReturn(Optional.of(log));
        when(repository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        Optional<NotificationLog> result = notificationService.markRead(1L);

        assertTrue(result.isPresent());
        assertTrue(result.get().getIsRead());
    }

    @Test
    void markRead_nonExisting_returnsEmpty() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertTrue(notificationService.markRead(999L).isEmpty());
    }

    @Test
    void getNotification_existing_returnsNotification() {
        NotificationLog log = new NotificationLog();
        log.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(log));
        assertTrue(notificationService.getNotification(1L).isPresent());
    }

    @Test
    void markAllRead_marksAllForUser() {
        NotificationLog n1 = new NotificationLog();
        n1.setIsRead(false);
        NotificationLog n2 = new NotificationLog();
        n2.setIsRead(false);

        when(repository.findByUserIdOrderBySentAtDesc(1L)).thenReturn(List.of(n1, n2));

        notificationService.markAllRead(1L);

        verify(repository, times(2)).save(any(NotificationLog.class));
    }

    @Test
    void deleteNotification_callsDeleteById() {
        notificationService.deleteNotification(1L);
        verify(repository).deleteById(1L);
    }

    @Test
    void createNotification_setsDefaults() {
        NotificationLog log = new NotificationLog();
        log.setUserId(1L);
        log.setTitle("Test");
        log.setMessage("Test message");

        when(repository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        NotificationLog result = notificationService.createNotification(log);

        assertNotNull(result.getSentAt());
        assertFalse(result.getIsRead());
    }

    @Test
    void createNotification_preservesExistingSentAt() {
        LocalDateTime custom = LocalDateTime.of(2025, 1, 1, 12, 0);
        NotificationLog log = new NotificationLog();
        log.setUserId(1L);
        log.setTitle("Test");
        log.setMessage("msg");
        log.setSentAt(custom);
        log.setIsRead(true);

        when(repository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        NotificationLog result = notificationService.createNotification(log);

        assertEquals(custom, result.getSentAt());
        assertTrue(result.getIsRead());
    }

    @Test
    void broadcast_callsSendTargetedNotifications() {
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                isNull(),
                anyNotificationUserListType()
        )).thenReturn(ResponseEntity.ok(null));

        notificationService.broadcast("Title", "Message", "announcement");

        verify(restTemplate).exchange(
                eq("http://user-service/users?role=STUDENT"),
                eq(HttpMethod.GET),
                isNull(),
                anyNotificationUserListType()
        );
        verify(restTemplate).exchange(
                eq("http://user-service/users?role=INSTRUCTOR"),
                eq(HttpMethod.GET),
                isNull(),
                anyNotificationUserListType()
        );
        verify(restTemplate).exchange(
                eq("http://user-service/users?role=ADMIN"),
                eq(HttpMethod.GET),
                isNull(),
                anyNotificationUserListType()
        );
        verify(repository, never()).save(any(NotificationLog.class));
    }
}
