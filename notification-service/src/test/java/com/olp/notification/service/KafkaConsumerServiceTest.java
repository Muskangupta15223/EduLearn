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
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerServiceTest {

    @Mock private NotificationLogRepository repository;
    @Mock private EmailService emailService;
    @Mock private RestTemplate restTemplate;

    private KafkaConsumerService kafkaConsumerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        kafkaConsumerService = new KafkaConsumerService(repository, objectMapper, emailService, restTemplate);
    }

    // --- User Events ---

    @Test
    void consumeUserEvents_signup_sendsWelcomeEmailAndSavesNotification() throws Exception {
        when(repository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        String message = objectMapper.writeValueAsString(Map.of(
                "eventType", "USER_SIGNUP",
                "userId", 1,
                "email", "user@test.com",
                "fullName", "Test User"
        ));

        kafkaConsumerService.consumeUserEvents(message);

        verify(emailService).sendWelcomeEmail("user@test.com", "Test User");
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(captor.capture());
        assertEquals("welcome", captor.getValue().getType());
        assertEquals("Welcome to EduLearn", captor.getValue().getTitle());
    }

    @Test
    void consumeUserEvents_login_savesLoginNotification() throws Exception {
        when(repository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        String message = objectMapper.writeValueAsString(Map.of(
                "eventType", "USER_LOGIN",
                "userId", 1,
                "fullName", "Test User",
                "email", "user@test.com"
        ));

        kafkaConsumerService.consumeUserEvents(message);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(captor.capture());
        assertEquals("login", captor.getValue().getType());
    }

    @Test
    void consumeUserEvents_passwordReset_sendsEmail() throws Exception {
        String message = objectMapper.writeValueAsString(Map.of(
                "eventType", "PASSWORD_RESET",
                "email", "user@test.com",
                "fullName", "Test User",
                "resetLink", "http://localhost:3000/reset?token=abc"
        ));

        kafkaConsumerService.consumeUserEvents(message);

        verify(emailService).sendPasswordResetEmail("user@test.com", "Test User",
                "http://localhost:3000/reset?token=abc");
    }

    @Test
    void consumeUserEvents_malformedJson_doesNotThrow() {
        assertDoesNotThrow(() -> kafkaConsumerService.consumeUserEvents("not json at all"));
    }

    // --- Course Events ---

    @Test
    void consumeCourseEvents_certificateIssued_savesNotification() throws Exception {
        when(repository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        String message = objectMapper.writeValueAsString(Map.of(
                "eventType", "CERTIFICATE_ISSUED",
                "userId", 5,
                "courseTitle", "Java 101",
                "certificateNo", "CERT-001"
        ));

        kafkaConsumerService.consumeCourseEvents(message);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(captor.capture());
        assertEquals("certificate", captor.getValue().getType());
        assertTrue(captor.getValue().getMessage().contains("Java 101"));
    }

    @Test
    void consumeCourseEvents_quizResult_savesNotification() throws Exception {
        when(repository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        String message = objectMapper.writeValueAsString(Map.of(
                "eventType", "QUIZ_RESULT",
                "userId", 1,
                "quizTitle", "Mid-Term Quiz",
                "score", 8,
                "maxScore", 10,
                "passed", true,
                "timedOut", false
        ));

        kafkaConsumerService.consumeCourseEvents(message);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(captor.capture());
        assertEquals("Quiz Passed", captor.getValue().getTitle());
        assertTrue(captor.getValue().getMessage().contains("8/10"));
    }

    @Test
    void consumeCourseEvents_assignmentGraded_savesNotification() throws Exception {
        when(repository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        String message = objectMapper.writeValueAsString(Map.of(
                "eventType", "ASSIGNMENT_GRADED",
                "userId", 1,
                "assignmentTitle", "Homework 1",
                "score", "90",
                "maxScore", "100",
                "feedback", "Great work"
        ));

        kafkaConsumerService.consumeCourseEvents(message);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(captor.capture());
        assertEquals("Assignment Graded", captor.getValue().getTitle());
        assertTrue(captor.getValue().getMessage().contains("90/100"));
    }

    @Test
    void consumeCourseEvents_malformedJson_doesNotThrow() {
        assertDoesNotThrow(() -> kafkaConsumerService.consumeCourseEvents("invalid"));
    }

    // --- Payment Events ---

    @Test
    void consumePaymentEvents_paymentSuccess_savesNotification() throws Exception {
        when(repository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        String message = objectMapper.writeValueAsString(Map.of(
                "eventType", "PAYMENT_SUCCESS",
                "userId", 1,
                "courseId", 100,
                "amount", "499",
                "currency", "INR",
                "courseTitle", "Python Basics"
        ));

        kafkaConsumerService.consumePaymentEvents(message);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(captor.capture());
        assertEquals("Payment Successful", captor.getValue().getTitle());
        assertTrue(captor.getValue().getMessage().contains("INR 499"));
    }

    @Test
    void consumePaymentEvents_refund_savesNotification() throws Exception {
        when(repository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        String message = objectMapper.writeValueAsString(Map.of(
                "eventType", "PAYMENT_REFUNDED",
                "userId", 1,
                "courseId", 100,
                "courseTitle", "Java Course"
        ));

        kafkaConsumerService.consumePaymentEvents(message);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(captor.capture());
        assertEquals("Refund Processed", captor.getValue().getTitle());
    }

    @Test
    void consumePaymentEvents_subscriptionActivated() throws Exception {
        when(repository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArgument(0));

        String message = objectMapper.writeValueAsString(Map.of(
                "eventType", "SUBSCRIPTION_PRO_ACTIVATED",
                "userId", 1,
                "plan", "PRO"
        ));

        kafkaConsumerService.consumePaymentEvents(message);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(repository).save(captor.capture());
        assertEquals("Subscription Activated", captor.getValue().getTitle());
    }

    @Test
    void consumePaymentEvents_malformedJson_doesNotThrow() {
        assertDoesNotThrow(() -> kafkaConsumerService.consumePaymentEvents("bad json"));
    }
}
