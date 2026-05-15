package com.olp.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.notification.model.NotificationLog;
import com.olp.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerServiceTest {

    @Mock
    private NotificationLogRepository repository;

    @Mock
    private EmailService emailService;

    @Mock
    private RestTemplate restTemplate;

    private KafkaConsumerService consumerService;

    @BeforeEach
    void setUp() {
        consumerService = new KafkaConsumerService(repository, new ObjectMapper(), emailService, restTemplate);
    }

    @Test
    void consumeCourseEventsCreatesQuizResultNotification() {
        String message = """
                {
                  "eventType":"QUIZ_RESULT",
                  "userId":7,
                  "courseId":12,
                  "quizTitle":"Java Basics",
                  "score":8,
                  "maxScore":10,
                  "passed":true,
                  "timedOut":false
                }
                """;

        consumerService.consumeCourseEvents(message);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(captor.capture());
        NotificationLog log = captor.getValue();
        assertEquals(7L, log.getUserId());
        assertEquals("quiz-result", log.getType());
        assertEquals("Quiz Passed", log.getTitle());
        assertTrue(log.getMessage().contains("8/10"));
    }

    @Test
    void consumeCourseEventsCreatesNotificationsForAssignmentCreated() {
        when(restTemplate.getForEntity("http://enrollment-service/enrollments/course/14", List.class))
                .thenReturn(ResponseEntity.ok(List.of(
                        Map.of("userId", 3L, "status", "ACTIVE"),
                        Map.of("userId", 4L, "status", "COMPLETED"),
                        Map.of("userId", 5L, "status", "PENDING_PAYMENT")
                )));

        String message = """
                {
                  "eventType":"ASSIGNMENT_CREATED",
                  "courseId":14,
                  "courseTitle":"Spring Boot Mastery",
                  "assignmentId":9,
                  "assignmentTitle":"Architecture Review",
                  "dueDate":"2026-05-30"
                }
                """;

        consumerService.consumeCourseEvents(message);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository, times(2)).save(captor.capture());
        List<NotificationLog> logs = captor.getAllValues();
        assertEquals("assignment", logs.get(0).getType());
        assertTrue(logs.get(0).getMessage().contains("Architecture Review"));
        assertTrue(logs.get(0).getMessage().contains("2026-05-30"));
    }

    @Test
    void consumeUserEventsCreatesNotificationForLogin() {
        String message = """
                {
                  "eventType":"USER_LOGIN",
                  "userId":8,
                  "email":"asha@example.com",
                  "fullName":"Asha"
                }
                """;

        consumerService.consumeUserEvents(message);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(captor.capture());
        NotificationLog log = captor.getValue();
        assertEquals(8L, log.getUserId());
        assertEquals("login", log.getType());
        assertEquals("Login Successful", log.getTitle());
        assertTrue(log.getMessage().contains("Asha"));
    }

    @Test
    void consumeCourseEventsCreatesNotificationsForAddedCourseContent() {
        when(restTemplate.getForEntity("http://enrollment-service/enrollments/course/22", List.class))
                .thenReturn(ResponseEntity.ok(List.of(
                        Map.of("userId", 31L, "status", "ACTIVE"),
                        Map.of("userId", 32L, "status", "COMPLETED"),
                        Map.of("userId", 33L, "status", "PENDING_PAYMENT")
                )));

        String message = """
                {
                  "eventType":"COURSE_CONTENT_ADDED",
                  "courseId":22,
                  "courseTitle":"React Complete",
                  "contentType":"lesson",
                  "contentTitle":"Hooks Deep Dive"
                }
                """;

        consumerService.consumeCourseEvents(message);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository, times(2)).save(captor.capture());
        List<NotificationLog> logs = captor.getAllValues();
        assertEquals(31L, logs.get(0).getUserId());
        assertEquals("lesson", logs.get(0).getType());
        assertEquals("New Lesson Added", logs.get(0).getTitle());
        assertTrue(logs.get(0).getMessage().contains("Hooks Deep Dive"));
    }

    @Test
    void consumeCourseEventsUsesEnrollmentEventCourseDetailsForInstructorNotification() {
        String message = """
                {
                  "eventType":"STUDENT_ENROLLED",
                  "courseId":44,
                  "courseTitle":"Microservices Fundamentals",
                  "userId":17,
                  "instructorId":91
                }
                """;

        consumerService.consumeCourseEvents(message);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository, times(2)).save(captor.capture());
        List<NotificationLog> logs = captor.getAllValues();
        assertEquals(17L, logs.get(0).getUserId());
        assertEquals(91L, logs.get(1).getUserId());
        assertTrue(logs.get(1).getMessage().contains("Microservices Fundamentals"));
        verify(restTemplate, never()).getForEntity("http://course-service/courses/44", Map.class);
    }

    @Test
    void consumeCourseEventsCreatesInstructorNotificationForRejectedCourse() {
        String message = """
                {
                  "eventType":"COURSE_REJECTED",
                  "instructorId":21,
                  "title":"Kafka Essentials",
                  "reviewComment":"Please improve the course outline."
                }
                """;

        consumerService.consumeCourseEvents(message);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(captor.capture());
        NotificationLog log = captor.getValue();
        assertEquals(21L, log.getUserId());
        assertEquals("course", log.getType());
        assertEquals("Course Needs Changes", log.getTitle());
        assertTrue(log.getMessage().contains("Please improve the course outline."));
    }

    @Test
    void consumeUserEventsIgnoresAvatarUpdatedEvent() {
        String message = """
                {
                  "eventType":"USER_AVATAR_UPDATED",
                  "userId":8,
                  "email":"asha@example.com",
                  "fullName":"Asha",
                  "avatarUrl":"https://new-avatar"
                }
                """;

        consumerService.consumeUserEvents(message);

        verify(emailService, never()).sendWelcomeEmail(any(), any());
        verify(repository, never()).save(any(NotificationLog.class));
    }
}
