package com.olp.notification.service;

import com.olp.notification.dto.TargetedNotificationRequest;
import com.olp.notification.dto.TargetedNotificationResponse;
import com.olp.notification.model.NotificationLog;
import com.olp.notification.repository.NotificationLogRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

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
    void sendTargetedNotificationsCreatesOneNotificationPerRecipient() {
        TargetedNotificationRequest request = new TargetedNotificationRequest();
        request.setRoles(List.of("STUDENT"));
        request.setTitle("Notice");
        request.setMessage("Hello");
        request.setType("system");

        when(restTemplate.getForObject(eq("http://user-service/users?role=STUDENT"), eq(List.class)))
                .thenReturn(List.of(
                        Map.of("id", 1L, "role", "STUDENT"),
                        Map.of("id", 2L, "role", "STUDENT")
                ));

        TargetedNotificationResponse response = notificationService.sendTargetedNotifications(request);

        assertEquals(2, response.getRecipients());
        verify(repository, times(2)).save(any(NotificationLog.class));
    }
}
