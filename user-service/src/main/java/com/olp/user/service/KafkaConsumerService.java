package com.olp.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.user.model.UserProfile;
import com.olp.user.repository.UserProfileRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    private final UserProfileRepository repository;
    private final ObjectMapper objectMapper;

    public KafkaConsumerService(UserProfileRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "user-events", groupId = "user-service-group")
    public void consumeUserEvents(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventType = node.has("eventType") ? node.get("eventType").asText() : "";
            
            if ("USER_SIGNUP".equals(eventType)) {
                Long userId = node.has("userId") ? node.get("userId").asLong() : null;
                String email = node.has("email") ? node.get("email").asText() : "";
                String fullName = node.has("fullName") ? node.get("fullName").asText() : "";
                String role = node.has("role") ? node.get("role").asText() : "STUDENT";
                String avatarUrl = node.hasNonNull("avatarUrl") ? node.get("avatarUrl").asText() : null;

                if (userId != null && !repository.existsById(userId)) {
                    UserProfile profile = new UserProfile();
                    profile.setId(userId); // Use same ID as AuthUser
                    profile.setEmail(email);
                    profile.setFullName(fullName);
                    profile.setRole(role);
                    profile.setAvatarUrl(avatarUrl);
                    repository.save(profile);
                    System.out.println("Created UserProfile for ID: " + userId);
                } else if (userId != null) {
                    repository.findById(userId).ifPresent(profile -> {
                        if (email != null && !email.isBlank()) {
                            profile.setEmail(email);
                        }
                        if (fullName != null && !fullName.isBlank()) {
                            profile.setFullName(fullName);
                        }
                        if (role != null && !role.isBlank()) {
                            profile.setRole(role);
                        }
                        if (avatarUrl != null) {
                            profile.setAvatarUrl(avatarUrl);
                        }
                        repository.save(profile);
                    });
                }
            } else if ("USER_AVATAR_UPDATED".equals(eventType)) {
                Long userId = node.has("userId") ? node.get("userId").asLong() : null;
                String avatarUrl = node.hasNonNull("avatarUrl") ? node.get("avatarUrl").asText() : null;

                if (userId != null && avatarUrl != null) {
                    repository.findById(userId).ifPresent(profile -> {
                        profile.setAvatarUrl(avatarUrl);
                        repository.save(profile);
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to process user event: " + e.getMessage());
        }
    }
}
