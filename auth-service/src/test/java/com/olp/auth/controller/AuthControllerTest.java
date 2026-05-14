package com.olp.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.auth.config.JwtUtil;
import com.olp.auth.model.AuthUser;
import com.olp.auth.repository.AuthUserRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthUserRepository authUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private AuthController controller;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKeyString", "0123456789abcdef0123456789abcdef");
        controller = new AuthController(authUserRepository, jwtUtil, passwordEncoder, kafkaTemplate);
        ReflectionTestUtils.setField(controller, "bootstrapAdminEnabled", true);
        ReflectionTestUtils.setField(controller, "bootstrapAdminEmail", "admin@example.com");
        ReflectionTestUtils.setField(controller, "bootstrapAdminPassword", "Admin@123");
        ReflectionTestUtils.setField(controller, "bootstrapAdminName", "Admin User");
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:5173");
    }

    @Test
    void meIncludesAvatarUrl() {
        String email = "asha@example.com";
        AuthUser user = new AuthUser();
        user.setId(9L);
        user.setEmail("asha@example.com");
        user.setName("Asha");
        user.setRole("STUDENT");
        user.setProvider("GOOGLE");
        user.setAvatarUrl("https://lh3.googleusercontent.com/a-photo");

        when(authUserRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(user));

        ResponseEntity<?> response = controller.me(email);

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("https://lh3.googleusercontent.com/a-photo", body.get("avatarUrl"));
    }

    @Test
    void loginSuccessPublishesAvatarUpdatedEventForExistingGoogleUser() throws Exception {
        OAuth2User oAuth2User = new StubOAuth2User(Map.of(
                "email", "asha@example.com",
                "name", "Asha",
                "picture", "https://new-avatar"
        ));
        AuthUser existing = new AuthUser();
        existing.setId(9L);
        existing.setEmail("asha@example.com");
        existing.setName("Asha");
        existing.setRole("STUDENT");
        existing.setProvider("GOOGLE");
        existing.setAvatarUrl("https://old-avatar");

        when(authUserRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(existing));
        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        controller.loginSuccess(oAuth2User);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(kafkaTemplate, org.mockito.Mockito.times(2))
                .send(org.mockito.Mockito.eq("user-events"), org.mockito.Mockito.eq("9"), messageCaptor.capture());
        JsonNode avatarEvent = new ObjectMapper().readTree(messageCaptor.getAllValues().get(0));
        JsonNode loginEvent = new ObjectMapper().readTree(messageCaptor.getAllValues().get(1));
        assertEquals("USER_AVATAR_UPDATED", avatarEvent.get("eventType").asText());
        assertEquals(9L, avatarEvent.get("userId").asLong());
        assertEquals("https://new-avatar", avatarEvent.get("avatarUrl").asText());
        assertEquals("USER_LOGIN", loginEvent.get("eventType").asText());
    }

    @Test
    void bootstrapAdminLoginCreatesAdminAndReturnsToken() {
        when(authUserRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Admin@123")).thenReturn("encoded-admin-password");
        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(invocation -> {
            AuthUser saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        ResponseEntity<?> response = controller.login(Map.of(
                "email", "admin@example.com",
                "password", "Admin@123"
        ));

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        AuthUser user = (AuthUser) body.get("user");
        assertNotNull(body.get("token"));
        assertEquals("ADMIN", user.getRole());
        assertEquals("admin@example.com", user.getEmail());
        assertEquals("encoded-admin-password", user.getPassword());
        verify(passwordEncoder).encode("Admin@123");
        verify(kafkaTemplate, org.mockito.Mockito.times(2)).send(eq("user-events"), eq("1"), any(String.class));
    }

    @Test
    void bootstrapAdminLoginUpgradesExistingUserToAdmin() {
        AuthUser existing = new AuthUser();
        existing.setId(22L);
        existing.setEmail("admin@example.com");
        existing.setName("Muskan");
        existing.setRole("STUDENT");
        existing.setProvider("LOCAL");
        existing.setPassword("old");

        when(authUserRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("Admin@123")).thenReturn("encoded-admin-password");
        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = controller.login(Map.of(
                "email", "admin@example.com",
                "password", "Admin@123"
        ));

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        AuthUser user = (AuthUser) body.get("user");
        assertEquals("ADMIN", user.getRole());
        assertEquals(22L, user.getId());
    }

    @Test
    void forgotPasswordUsesViteFrontendResetUrl() throws Exception {
        AuthUser user = new AuthUser();
        user.setId(4L);
        user.setEmail("asha@example.com");
        user.setName("Asha");
        user.setRole("STUDENT");
        user.setProvider("LOCAL");
        user.setPassword("encoded");

        when(authUserRepository.findByEmail("asha@example.com")).thenReturn(Optional.of(user));

        controller.forgotPassword(Map.of("email", "asha@example.com"));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(kafkaTemplate).send(org.mockito.Mockito.eq("user-events"), messageCaptor.capture());
        JsonNode event = new ObjectMapper().readTree(messageCaptor.getValue());
        String resetLink = event.get("resetLink").asText();
        assertTrue(resetLink.startsWith("http://localhost:5173/reset-password?token="));
    }

    @Test
    void registerDoesNotAllowAdminSelfRegistration() {
        when(authUserRepository.findByEmail("asha@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Pass@123")).thenReturn("encoded-password");
        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(invocation -> {
            AuthUser saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        ResponseEntity<?> response = controller.register(Map.of(
                "email", "asha@example.com",
                "password", "Pass@123",
                "name", "Asha",
                "role", "ADMIN"
        ));

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        AuthUser user = (AuthUser) body.get("user");
        assertEquals("STUDENT", user.getRole());
    }

    @Test
    void registerKeepsInstructorRoleForInstructorSignups() {
        when(authUserRepository.findByEmail("mentor@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Pass@123")).thenReturn("encoded-password");
        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(invocation -> {
            AuthUser saved = invocation.getArgument(0);
            saved.setId(43L);
            return saved;
        });

        ResponseEntity<?> response = controller.register(Map.of(
                "email", "mentor@example.com",
                "password", "Pass@123",
                "name", "Mentor",
                "role", "INSTRUCTOR"
        ));

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        AuthUser user = (AuthUser) body.get("user");
        assertEquals("INSTRUCTOR", user.getRole());
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
