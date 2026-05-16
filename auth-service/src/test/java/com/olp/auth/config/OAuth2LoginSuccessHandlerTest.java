package com.olp.auth.config;

import com.olp.auth.model.AuthUser;
import com.olp.auth.repository.AuthUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
// import java.net.URLDecoder;
// import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock
    private AuthUserRepository authUserRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private Authentication authentication;

    @Mock
    private HttpServletResponse response;

    private OAuth2LoginSuccessHandler handler;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKeyString", "0123456789abcdef0123456789abcdef");
        handler = new OAuth2LoginSuccessHandler(authUserRepository, jwtUtil, kafkaTemplate);
        ReflectionTestUtils.setField(handler, "frontendUrl", "http://localhost:5173");
    }

    @Test
    void createsStudentUserAndRedirectsWithJwtForNewGoogleLogin() throws Exception {
        OAuth2User oAuth2User = new StubOAuth2User(Map.of(
                "email", "asha@example.com",
                "name", "Asha",
                "picture", "https://lh3.googleusercontent.com/a-photo"
        ));
        AuthUser saved = new AuthUser();
        saved.setId(21L);
        saved.setEmail("asha@example.com");
        saved.setName("Asha");
        saved.setRole("STUDENT");
        saved.setProvider("GOOGLE");
        saved.setAvatarUrl("https://lh3.googleusercontent.com/a-photo");

        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(authUserRepository.findByEmail("asha@example.com")).thenReturn(Optional.empty());
        when(authUserRepository.save(any(AuthUser.class))).thenReturn(saved);

        handler.onAuthenticationSuccess(null, response, authentication);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        ArgumentCaptor<String> kafkaMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, org.mockito.Mockito.times(2))
                .send(org.mockito.Mockito.eq("user-events"), org.mockito.Mockito.eq("21"), kafkaMessageCaptor.capture());

        String redirect = redirectCaptor.getValue();
        assertTrue(redirect.startsWith("http://localhost:5173/login?token="));
        assertTrue(redirect.contains("userId=21"));
        assertTrue(redirect.contains("email=asha%40example.com"));
        assertTrue(redirect.contains("role=STUDENT"));
        assertTrue(redirect.contains("avatarUrl=https%3A%2F%2Flh3.googleusercontent.com%2Fa-photo"));

        JsonNode signupEvent = new ObjectMapper().readTree(kafkaMessageCaptor.getAllValues().get(0));
        JsonNode loginEvent = new ObjectMapper().readTree(kafkaMessageCaptor.getAllValues().get(1));
        assertEquals("USER_SIGNUP", signupEvent.get("eventType").asText());
        assertEquals("https://lh3.googleusercontent.com/a-photo", signupEvent.get("avatarUrl").asText());
        assertEquals("USER_LOGIN", loginEvent.get("eventType").asText());
    }

    @Test
    void reusesExistingGoogleUserWithoutPublishingSignupAgain() throws Exception {
        OAuth2User oAuth2User = new StubOAuth2User(Map.of("email", "existing@example.com", "name", "Existing User"));
        AuthUser existing = new AuthUser();
        existing.setId(8L);
        existing.setEmail("existing@example.com");
        existing.setName("Existing User");
        existing.setRole("INSTRUCTOR");
        existing.setProvider("GOOGLE");

        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(authUserRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existing));

        handler.onAuthenticationSuccess(null, response, authentication);

        verify(authUserRepository, never()).save(any(AuthUser.class));
        ArgumentCaptor<String> kafkaMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(org.mockito.Mockito.eq("user-events"), org.mockito.Mockito.eq("8"), kafkaMessageCaptor.capture());
        JsonNode loginEvent = new ObjectMapper().readTree(kafkaMessageCaptor.getValue());
        assertEquals("USER_LOGIN", loginEvent.get("eventType").asText());
        verify(response).sendRedirect(org.mockito.ArgumentMatchers.contains("/login?token="));
    }

    @Test
    void updatesExistingGoogleUserAvatarWhenPictureChanges() throws Exception {
        OAuth2User oAuth2User = new StubOAuth2User(Map.of(
                "email", "existing@example.com",
                "name", "Existing User",
                "picture", "https://new-avatar"
        ));
        AuthUser existing = new AuthUser();
        existing.setId(8L);
        existing.setEmail("existing@example.com");
        existing.setName("Existing User");
        existing.setRole("INSTRUCTOR");
        existing.setProvider("GOOGLE");
        existing.setAvatarUrl("https://old-avatar");

        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(authUserRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existing));
        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        handler.onAuthenticationSuccess(null, response, authentication);

        verify(authUserRepository).save(existing);
        assertEquals("https://new-avatar", existing.getAvatarUrl());
        ArgumentCaptor<String> kafkaMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, org.mockito.Mockito.times(2))
                .send(org.mockito.Mockito.eq("user-events"), org.mockito.Mockito.eq("8"), kafkaMessageCaptor.capture());
        JsonNode avatarEvent = new ObjectMapper().readTree(kafkaMessageCaptor.getAllValues().get(0));
        JsonNode loginEvent = new ObjectMapper().readTree(kafkaMessageCaptor.getAllValues().get(1));
        assertEquals("USER_AVATAR_UPDATED", avatarEvent.get("eventType").asText());
        assertEquals(8L, avatarEvent.get("userId").asLong());
        assertEquals("https://new-avatar", avatarEvent.get("avatarUrl").asText());
        assertEquals("USER_LOGIN", loginEvent.get("eventType").asText());
        verify(response).sendRedirect(org.mockito.ArgumentMatchers.contains("/login?token="));
    }

    private static final class StubOAuth2User implements OAuth2User {
        private final Map<String, Object> attributes;

        private StubOAuth2User(Map<String, Object> attributes) {
            this.attributes = attributes;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return java.util.List.of();
        }

        @Override
        public String getName() {
            return String.valueOf(attributes.getOrDefault("name", "user"));
        }
    }
}
