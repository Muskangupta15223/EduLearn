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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerServiceTest {

    @Mock
    private UserProfileRepository repository;

    private KafkaConsumerService consumerService;

    @BeforeEach
    void setUp() {
        consumerService = new KafkaConsumerService(repository, new ObjectMapper());
    }

    @Test
    void createsUserProfileWithAvatarFromSignupEvent() {
        when(repository.existsById(8L)).thenReturn(false);

        consumerService.consumeUserEvents("""
                {
                  "eventType":"USER_SIGNUP",
                  "userId":8,
                  "email":"asha@example.com",
                  "fullName":"Asha",
                  "role":"STUDENT",
                  "avatarUrl":"https://lh3.googleusercontent.com/avatar"
                }
                """);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(repository).save(captor.capture());
        assertEquals("https://lh3.googleusercontent.com/avatar", captor.getValue().getAvatarUrl());
        assertEquals("asha@example.com", captor.getValue().getEmail());
    }

    @Test
    void updatesAvatarForExistingUserWhenSignupEventContainsNewPhoto() {
        UserProfile existing = new UserProfile();
        existing.setId(11L);
        existing.setRole("STUDENT");
        existing.setAvatarUrl("old");

        when(repository.existsById(11L)).thenReturn(true);
        when(repository.findById(11L)).thenReturn(Optional.of(existing));

        consumerService.consumeUserEvents("""
                {
                  "eventType":"USER_SIGNUP",
                  "userId":11,
                  "email":"user@example.com",
                  "fullName":"Existing User",
                  "role":"ADMIN",
                  "avatarUrl":"https://new-avatar"
                }
                """);

        verify(repository).save(any(UserProfile.class));
        assertEquals("https://new-avatar", existing.getAvatarUrl());
        assertEquals("ADMIN", existing.getRole());
        assertEquals("Existing User", existing.getFullName());
        assertEquals("user@example.com", existing.getEmail());
    }

    @Test
    void updatesAvatarForExistingUserFromAvatarUpdatedEvent() {
        UserProfile existing = new UserProfile();
        existing.setId(14L);
        existing.setAvatarUrl("old");

        when(repository.findById(14L)).thenReturn(Optional.of(existing));

        consumerService.consumeUserEvents("""
                {
                  "eventType":"USER_AVATAR_UPDATED",
                  "userId":14,
                  "avatarUrl":"https://updated-avatar"
                }
                """);

        verify(repository).save(any(UserProfile.class));
        assertEquals("https://updated-avatar", existing.getAvatarUrl());
    }
}
