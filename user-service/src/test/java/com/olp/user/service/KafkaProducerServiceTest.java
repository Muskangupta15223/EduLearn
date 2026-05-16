package com.olp.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.user.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaProducerServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaProducerService kafkaProducerService;

    @BeforeEach
    void setUp() {
        kafkaProducerService = new KafkaProducerService(kafkaTemplate, new ObjectMapper());
    }

    @Test
    void sendUserSignupEvent_sendsToKafka() {
        kafkaProducerService.sendUserSignupEvent(1L, "user@test.com", "Test User", "http://avatar.jpg");

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), messageCaptor.capture());

        assertEquals("user-events", topicCaptor.getValue());
        assertEquals("1", keyCaptor.getValue());
        assertTrue(messageCaptor.getValue().contains("USER_SIGNUP"));
        assertTrue(messageCaptor.getValue().contains("user@test.com"));
    }

    @Test
    void sendUserRoleUpdatedEvent_sendsToKafka() {
        kafkaProducerService.sendUserRoleUpdatedEvent(2L, "prof@test.com", "Professor", "INSTRUCTOR");

        verify(kafkaTemplate).send(eq("user-events"), eq("2"), anyString());
    }

    @Test
    void sendUserProfileUpdatedEvent_sendsToKafka() {
        UserProfile profile = new UserProfile();
        profile.setId(3L);
        profile.setEmail("updated@test.com");
        profile.setFullName("Updated User");
        profile.setRole("STUDENT");
        profile.setAvatarUrl("http://avatar.jpg");
        profile.setMobile("12345");
        profile.setBio("Bio text");

        kafkaProducerService.sendUserProfileUpdatedEvent(profile);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("user-events"), eq("3"), messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("USER_PROFILE_UPDATED"));
        assertTrue(message.contains("updated@test.com"));
        assertTrue(message.contains("Updated User"));
    }

    @Test
    void sendUserSignupEvent_withNullValues_doesNotThrow() {
        assertDoesNotThrow(() ->
                kafkaProducerService.sendUserSignupEvent(1L, null, null, null));

        verify(kafkaTemplate).send(eq("user-events"), eq("1"), anyString());
    }

    @Test
    void sendUserRoleUpdatedEvent_withNullValues_doesNotThrow() {
        assertDoesNotThrow(() ->
                kafkaProducerService.sendUserRoleUpdatedEvent(1L, null, null, null));

        verify(kafkaTemplate).send(eq("user-events"), eq("1"), anyString());
    }
}
