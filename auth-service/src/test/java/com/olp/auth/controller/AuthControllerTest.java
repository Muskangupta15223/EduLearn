package com.olp.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.auth.config.JwtUtil;
import com.olp.auth.model.AuthUser;
import com.olp.auth.repository.AuthUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthUserRepository authUserRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(authUserRepository, jwtUtil, passwordEncoder, kafkaTemplate);
        ReflectionTestUtils.setField(authController, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(authController, "bootstrapAdminEnabled", false);
        ReflectionTestUtils.setField(authController, "bootstrapAdminEmail", "");
        ReflectionTestUtils.setField(authController, "bootstrapAdminPassword", "");
        ReflectionTestUtils.setField(authController, "bootstrapAdminName", "Admin");
    }

    // --- Registration Tests ---

    @Test
    void register_success() {
        when(authUserRepository.findByEmail("user@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("$2a$encoded");
        AuthUser saved = createUser(1L, "user@test.com", "Test User", "STUDENT");
        when(authUserRepository.save(any(AuthUser.class))).thenReturn(saved);
        when(jwtUtil.generateToken(anyString(), anyString(), anyLong(), anyString())).thenReturn("jwt-token");

        var request = new com.olp.auth.dto.AuthDtos.RegisterRequest("user@test.com", "Test User", "password123", null);
        ResponseEntity<?> response = authController.register(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void register_missingEmail_returnsBadRequest() {
        var request = new com.olp.auth.dto.AuthDtos.RegisterRequest("", "Test", "pass", null);
        ResponseEntity<?> response = authController.register(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void register_missingPassword_returnsBadRequest() {
        var request = new com.olp.auth.dto.AuthDtos.RegisterRequest("user@test.com", "Test", "", null);
        ResponseEntity<?> response = authController.register(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void register_duplicateEmail_returnsBadRequest() {
        when(authUserRepository.findByEmail("existing@test.com")).thenReturn(Optional.of(new AuthUser()));

        var request = new com.olp.auth.dto.AuthDtos.RegisterRequest("existing@test.com", "Test", "pass", null);
        ResponseEntity<?> response = authController.register(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void register_withInstructorRole_setsInstructorRole() {
        when(authUserRepository.findByEmail("instructor@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$encoded");
        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(i -> {
            AuthUser u = i.getArgument(0);
            u.setId(2L);
            return u;
        });
        when(jwtUtil.generateToken(anyString(), anyString(), anyLong(), anyString())).thenReturn("token");

        var request = new com.olp.auth.dto.AuthDtos.RegisterRequest("instructor@test.com", "Prof", "pass", "INSTRUCTOR");
        ResponseEntity<?> response = authController.register(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // --- Login Tests ---

    @Test
    void login_success() {
        AuthUser user = createUser(1L, "user@test.com", "Test", "STUDENT");
        user.setPassword("$2a$hashedPass");
        when(authUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "$2a$hashedPass")).thenReturn(true);
        when(jwtUtil.generateToken(anyString(), anyString(), anyLong(), anyString())).thenReturn("jwt");

        var request = new com.olp.auth.dto.AuthDtos.LoginRequest("user@test.com", "password");
        ResponseEntity<?> response = authController.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void login_wrongPassword_returnsUnauthorized() {
        AuthUser user = createUser(1L, "user@test.com", "Test", "STUDENT");
        user.setPassword("$2a$hashedPass");
        when(authUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$hashedPass")).thenReturn(false);

        var request = new com.olp.auth.dto.AuthDtos.LoginRequest("user@test.com", "wrong");
        ResponseEntity<?> response = authController.login(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void login_userNotFound_returnsUnauthorized() {
        when(authUserRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        var request = new com.olp.auth.dto.AuthDtos.LoginRequest("nobody@test.com", "pass");
        ResponseEntity<?> response = authController.login(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void login_deactivatedUser_returnsForbidden() {
        AuthUser user = createUser(1L, "user@test.com", "Test", "DEACTIVATED");
        user.setPassword("$2a$hashedPass");
        when(authUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        var request = new com.olp.auth.dto.AuthDtos.LoginRequest("user@test.com", "pass");
        ResponseEntity<?> response = authController.login(request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void login_emptyCredentials_returnsUnauthorized() {
        var request = new com.olp.auth.dto.AuthDtos.LoginRequest("", "");
        ResponseEntity<?> response = authController.login(request);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // --- /auth/me Tests ---

    @Test
    void me_validEmail_returnsProfile() {
        AuthUser user = createUser(1L, "user@test.com", "Test", "STUDENT");
        when(authUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        ResponseEntity<?> response = authController.me("user@test.com");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void me_noEmail_returnsUnauthorized() {
        ResponseEntity<?> response = authController.me(null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void me_deactivatedUser_returnsForbidden() {
        AuthUser user = createUser(1L, "user@test.com", "Test", "DEACTIVATED");
        when(authUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        ResponseEntity<?> response = authController.me("user@test.com");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // --- /auth/refresh Tests ---

    @Test
    void refresh_validEmail_returnsNewToken() {
        AuthUser user = createUser(1L, "user@test.com", "Test", "STUDENT");
        when(authUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(anyString(), anyString(), anyLong(), anyString())).thenReturn("new-jwt");

        ResponseEntity<?> response = authController.refresh("user@test.com");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void refresh_noEmail_returnsUnauthorized() {
        ResponseEntity<?> response = authController.refresh(null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // --- /auth/validate Tests ---

    @Test
    void validate_validToken_returnsTrue() {
        when(jwtUtil.validateToken("good-token")).thenReturn(true);
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.getSubject()).thenReturn("user@test.com");
        when(claims.get("role", String.class)).thenReturn("STUDENT");
        when(claims.get("userId")).thenReturn(1);
        when(claims.get("name", String.class)).thenReturn("Test");
        when(claims.getExpiration()).thenReturn(new java.util.Date());
        when(jwtUtil.extractAllClaims("good-token")).thenReturn(claims);

        var request = new com.olp.auth.dto.AuthDtos.TokenRequest("good-token");
        ResponseEntity<?> response = authController.validate(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void validate_invalidToken_returnsUnauthorized() {
        when(jwtUtil.validateToken("bad-token")).thenReturn(false);

        var request = new com.olp.auth.dto.AuthDtos.TokenRequest("bad-token");
        ResponseEntity<?> response = authController.validate(request);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void validate_emptyToken_returnsBadRequest() {
        var request = new com.olp.auth.dto.AuthDtos.TokenRequest("");
        ResponseEntity<?> response = authController.validate(request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // --- /auth/password Tests ---

    @Test
    void changePassword_success() {
        AuthUser user = createUser(1L, "user@test.com", "Test", "STUDENT");
        user.setPassword("$2a$oldHash");
        when(authUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass", "$2a$oldHash")).thenReturn(true);
        when(passwordEncoder.encode("newPass")).thenReturn("$2a$newHash");

        var request = new com.olp.auth.dto.AuthDtos.ChangePasswordRequest("oldPass", "newPass");
        ResponseEntity<?> response = authController.changePassword("user@test.com", request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void changePassword_wrongCurrent_returnsUnauthorized() {
        AuthUser user = createUser(1L, "user@test.com", "Test", "STUDENT");
        user.setPassword("$2a$oldHash");
        when(authUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPass", "$2a$oldHash")).thenReturn(false);

        var request = new com.olp.auth.dto.AuthDtos.ChangePasswordRequest("wrongPass", "newPass");
        ResponseEntity<?> response = authController.changePassword("user@test.com", request);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // --- /auth/profile Tests ---

    @Test
    void updateProfile_success() {
        AuthUser user = createUser(1L, "user@test.com", "Test", "STUDENT");
        when(authUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(i -> i.getArgument(0));

        var request = new com.olp.auth.dto.AuthDtos.ProfileUpdateRequest("New Name", null, "12345", "bio");
        ResponseEntity<?> response = authController.updateProfile("user@test.com", request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void updateProfile_noEmail_returnsUnauthorized() {
        var request = new com.olp.auth.dto.AuthDtos.ProfileUpdateRequest("Name", null, null, null);
        ResponseEntity<?> response = authController.updateProfile(null, request);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // --- /auth/logout Tests ---

    @Test
    void logout_returnsOk() {
        ResponseEntity<?> response = authController.logout();
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // --- /auth/public/ping Tests ---

    @Test
    void ping_returnsServiceUp() {
        assertEquals("auth-service-up", authController.ping());
    }

    // --- Forgot/Reset Password Tests ---

    @Test
    void forgotPassword_unknownEmail_returnsOk() {
        when(authUserRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        var request = new com.olp.auth.dto.AuthDtos.ForgotPasswordRequest("unknown@test.com");
        ResponseEntity<?> response = authController.forgotPassword(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void forgotPassword_knownEmail_returnsOk() {
        AuthUser user = createUser(1L, "user@test.com", "Test", "STUDENT");
        when(authUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        var request = new com.olp.auth.dto.AuthDtos.ForgotPasswordRequest("user@test.com");
        ResponseEntity<?> response = authController.forgotPassword(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void resetPassword_nullFields_returnsBadRequest() {
        var request = new com.olp.auth.dto.AuthDtos.ResetPasswordRequest(null, null);
        ResponseEntity<?> response = authController.resetPassword(request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void resetPassword_invalidToken_returnsBadRequest() {
        var request = new com.olp.auth.dto.AuthDtos.ResetPasswordRequest("no-such-token", "newPass");
        ResponseEntity<?> response = authController.resetPassword(request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // --- Helper ---

    private AuthUser createUser(Long id, String email, String name, String role) {
        AuthUser user = new AuthUser();
        user.setId(id);
        user.setEmail(email);
        user.setName(name);
        user.setRole(role);
        user.setProvider("LOCAL");
        return user;
    }
}
