package com.olp.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.auth.model.AuthUser;
import com.olp.auth.repository.AuthUserRepository;
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
    private AuthUserRepository authUserRepository;

    private KafkaConsumerService kafkaConsumerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        kafkaConsumerService = new KafkaConsumerService(authUserRepository, objectMapper);
    }

    @Test
    void consumeUserEvents_roleUpdated_updatesUserRole() throws Exception {
        AuthUser user = new AuthUser();
        user.setId(1L);
        user.setEmail("test@edu.com");
        user.setRole("STUDENT");

        when(authUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(i -> i.getArgument(0));

        String message = objectMapper.writeValueAsString(java.util.Map.of(
                "eventType", "USER_ROLE_UPDATED",
                "userId", 1L,
                "role", "INSTRUCTOR"
        ));

        kafkaConsumerService.consumeUserEvents(message);

        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserRepository).save(captor.capture());
        assertEquals("INSTRUCTOR", captor.getValue().getRole());
    }

    @Test
    void consumeUserEvents_profileUpdated_updatesUserFields() throws Exception {
        AuthUser user = new AuthUser();
        user.setId(2L);
        user.setEmail("user@edu.com");
        user.setRole("STUDENT");

        when(authUserRepository.findById(2L)).thenReturn(Optional.of(user));
        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(i -> i.getArgument(0));

        String message = objectMapper.writeValueAsString(java.util.Map.of(
                "eventType", "USER_PROFILE_UPDATED",
                "userId", 2L,
                "fullName", "Updated Name",
                "avatarUrl", "http://avatar.jpg",
                "mobile", "9876543210",
                "bio", "New bio"
        ));

        kafkaConsumerService.consumeUserEvents(message);

        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserRepository).save(captor.capture());
        AuthUser saved = captor.getValue();
        assertEquals("Updated Name", saved.getName());
        assertEquals("http://avatar.jpg", saved.getAvatarUrl());
        assertEquals("9876543210", saved.getMobile());
        assertEquals("New bio", saved.getBio());
    }

    @Test
    void consumeUserEvents_ignoresUnrelatedEvent() throws Exception {
        String message = objectMapper.writeValueAsString(java.util.Map.of(
                "eventType", "USER_SIGNUP",
                "userId", 1L
        ));

        kafkaConsumerService.consumeUserEvents(message);

        verify(authUserRepository, never()).save(any());
    }

    @Test
    void consumeUserEvents_userNotFound_doesNotSave() throws Exception {
        when(authUserRepository.findById(999L)).thenReturn(Optional.empty());

        String message = objectMapper.writeValueAsString(java.util.Map.of(
                "eventType", "USER_ROLE_UPDATED",
                "userId", 999L,
                "role", "ADMIN"
        ));

        kafkaConsumerService.consumeUserEvents(message);

        verify(authUserRepository, never()).save(any());
    }

    @Test
    void consumeUserEvents_lookupByEmail_whenUserIdNotFound() throws Exception {
        AuthUser user = new AuthUser();
        user.setId(5L);
        user.setEmail("found@email.com");
        user.setRole("STUDENT");

        when(authUserRepository.findById(5L)).thenReturn(Optional.empty());
        when(authUserRepository.findByEmail("found@email.com")).thenReturn(Optional.of(user));
        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(i -> i.getArgument(0));

        String message = "{\"eventType\":\"USER_ROLE_UPDATED\",\"userId\":5,\"email\":\"found@email.com\",\"role\":\"ADMIN\"}";

        kafkaConsumerService.consumeUserEvents(message);

        verify(authUserRepository).save(any(AuthUser.class));
    }

    @Test
    void consumeUserEvents_malformedJson_doesNotThrow() {
        assertDoesNotThrow(() -> kafkaConsumerService.consumeUserEvents("not valid json"));
        verify(authUserRepository, never()).save(any());
    }
}
