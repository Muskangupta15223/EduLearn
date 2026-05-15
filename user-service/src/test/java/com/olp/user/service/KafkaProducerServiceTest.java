package com.olp.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaProducerServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void includesAvatarUrlInSignupEvent() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        KafkaProducerService producerService = new KafkaProducerService(kafkaTemplate, objectMapper);

        producerService.sendUserSignupEvent(7L, "asha@example.com", "Asha", "https://avatar");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(org.mockito.Mockito.eq("user-events"), org.mockito.Mockito.eq("7"), messageCaptor.capture());

        JsonNode event = objectMapper.readTree(messageCaptor.getValue());
        assertEquals("USER_SIGNUP", event.get("eventType").asText());
        assertEquals(7L, event.get("userId").asLong());
        assertEquals("asha@example.com", event.get("email").asText());
        assertEquals("Asha", event.get("fullName").asText());
        assertEquals("https://avatar", event.get("avatarUrl").asText());
    }
}
