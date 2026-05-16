package com.olp.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.user.model.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendUserSignupEvent(Long userId, String email, String fullName, String avatarUrl) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_SIGNUP");
        event.put("userId", userId);
        event.put("email", email);
        event.put("fullName", fullName);
        event.put("avatarUrl", avatarUrl);

        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("user-events", String.valueOf(userId), message);
        } catch (JsonProcessingException e) {
            log.error("Error serializing user signup event: {}", e.getMessage(), e);
        }
    }

    public void sendUserRoleUpdatedEvent(Long userId, String email, String fullName, String role) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_ROLE_UPDATED");
        event.put("userId", userId);
        event.put("email", email);
        event.put("fullName", fullName);
        event.put("role", role);
        sendEvent(userId, event);
    }

    public void sendUserProfileUpdatedEvent(UserProfile profile) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_PROFILE_UPDATED");
        event.put("userId", profile.getId());
        event.put("email", profile.getEmail());
        event.put("fullName", profile.getFullName());
        event.put("role", profile.getRole());
        event.put("avatarUrl", profile.getAvatarUrl());
        event.put("mobile", profile.getMobile());
        event.put("bio", profile.getBio());
        sendEvent(profile.getId(), event);
    }

    private void sendEvent(Long key, Map<String, Object> event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("user-events", String.valueOf(key), message);
        } catch (JsonProcessingException e) {
            log.error("Error serializing user event: {}", e.getMessage(), e);
        }
    }
}
