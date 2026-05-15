package com.olp.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.auth.model.AuthUser;
import com.olp.auth.repository.AuthUserRepository;
import java.util.Optional;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    private final AuthUserRepository authUserRepository;
    private final ObjectMapper objectMapper;

    public KafkaConsumerService(AuthUserRepository authUserRepository, ObjectMapper objectMapper) {
        this.authUserRepository = authUserRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "user-events", groupId = "auth-service-group")
    public void consumeUserEvents(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventType = node.path("eventType").asText("");
            if (!"USER_ROLE_UPDATED".equals(eventType) && !"USER_PROFILE_UPDATED".equals(eventType)) {
                return;
            }

            Optional<AuthUser> existing = findTargetUser(node);
            if (existing.isEmpty()) {
                return;
            }

            AuthUser user = existing.get();
            if (node.hasNonNull("role")) {
                user.setRole(node.get("role").asText());
            }
            if (node.hasNonNull("fullName")) {
                user.setName(node.get("fullName").asText());
            }
            if (node.hasNonNull("avatarUrl")) {
                user.setAvatarUrl(node.get("avatarUrl").asText());
            }
            if (node.hasNonNull("mobile")) {
                user.setMobile(node.get("mobile").asText());
            }
            if (node.hasNonNull("bio")) {
                user.setBio(node.get("bio").asText());
            }
            authUserRepository.save(user);
        } catch (Exception e) {
            System.err.println("Failed to process auth user sync event: " + e.getMessage());
        }
    }

    private Optional<AuthUser> findTargetUser(JsonNode node) {
        if (node.hasNonNull("userId")) {
            Optional<AuthUser> byId = authUserRepository.findById(node.get("userId").asLong());
            if (byId.isPresent()) {
                return byId;
            }
        }
        if (node.hasNonNull("email")) {
            return authUserRepository.findByEmail(node.get("email").asText().trim().toLowerCase());
        }
        return Optional.empty();
    }
}
