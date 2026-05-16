package com.olp.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.user.model.UserProfile;
import com.olp.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerServiceTest {

    @Mock
    private UserProfileRepository repository;

    private KafkaConsumerService kafkaConsumerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        kafkaConsumerService = new KafkaConsumerService(repository, objectMapper);
    }

    @Test
    void consumeUserEvents_signup_createsProfile() throws Exception {
        when(repository.existsById(1L)).thenReturn(false);
        when(repository.save(any(UserProfile.class))).thenAnswer(i -> i.getArgument(0));

        String message = objectMapper.writeValueAsString(java.util.Map.of(
                "eventType", "USER_SIGNUP",
                "userId", 1,
                "email", "user@test.com",
                "fullName", "Test User",
                "role", "STUDENT"
        ));

        kafkaConsumerService.consumeUserEvents(message);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(repository).save(captor.capture());
        assertEquals("user@test.com", captor.getValue().getEmail());
        assertEquals("Test User", captor.getValue().getFullName());
        assertEquals("STUDENT", captor.getValue().getRole());
    }

    @Test
    void consumeUserEvents_signup_existingUser_updatesFields() throws Exception {
        UserProfile existing = new UserProfile();
        existing.setId(1L);
        existing.setEmail("old@test.com");
        existing.setFullName("Old Name");

        when(repository.existsById(1L)).thenReturn(true);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(UserProfile.class))).thenAnswer(i -> i.getArgument(0));

        String message = objectMapper.writeValueAsString(java.util.Map.of(
                "eventType", "USER_SIGNUP",
                "userId", 1,
                "email", "updated@test.com",
                "fullName", "Updated Name",
                "role", "INSTRUCTOR"
        ));

        kafkaConsumerService.consumeUserEvents(message);

        verify(repository).save(any(UserProfile.class));
    }

    @Test
    void consumeUserEvents_avatarUpdated_updatesAvatar() throws Exception {
        UserProfile existing = new UserProfile();
        existing.setId(2L);
        existing.setAvatarUrl("old-avatar.jpg");

        when(repository.findById(2L)).thenReturn(Optional.of(existing));
        when(repository.save(any(UserProfile.class))).thenAnswer(i -> i.getArgument(0));

        String message = "{\"eventType\":\"USER_AVATAR_UPDATED\",\"userId\":2,\"avatarUrl\":\"new-avatar.jpg\"}";

        kafkaConsumerService.consumeUserEvents(message);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(repository).save(captor.capture());
        assertEquals("new-avatar.jpg", captor.getValue().getAvatarUrl());
    }

    @Test
    void consumeUserEvents_avatarUpdated_missingUserId_doesNotSave() throws Exception {
        String message = "{\"eventType\":\"USER_AVATAR_UPDATED\",\"avatarUrl\":\"new-avatar.jpg\"}";

        kafkaConsumerService.consumeUserEvents(message);

        verify(repository, never()).save(any());
    }

    @Test
    void consumeUserEvents_malformedJson_doesNotThrow() {
        assertDoesNotThrow(() -> kafkaConsumerService.consumeUserEvents("broken json"));
        verify(repository, never()).save(any());
    }

    @Test
    void consumeUserEvents_unknownEvent_doesNotSave() throws Exception {
        String message = objectMapper.writeValueAsString(java.util.Map.of(
                "eventType", "UNKNOWN_EVENT",
                "userId", 1
        ));

        kafkaConsumerService.consumeUserEvents(message);
        verify(repository, never()).save(any());
    }
}
