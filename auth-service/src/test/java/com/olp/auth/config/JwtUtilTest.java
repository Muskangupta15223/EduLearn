package com.olp.auth.config;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // Set a valid 32+ char secret for testing
        ReflectionTestUtils.setField(jwtUtil, "secretKeyString",
                "testSecretKeyAtLeast32CharactersLong!");
    }

    @Test
    void generateToken_returnsNonNullToken() {
        String token = jwtUtil.generateToken("user@test.com", "STUDENT", 1L, "Test User");
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void validateToken_returnsTrueForValidToken() {
        String token = jwtUtil.generateToken("user@test.com", "STUDENT", 1L, "Test");
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void validateToken_returnsFalseForInvalidToken() {
        assertFalse(jwtUtil.validateToken("invalid.token.string"));
    }

    @Test
    void validateToken_returnsFalseWhenSecretNotConfigured() {
        JwtUtil unconfigured = new JwtUtil();
        ReflectionTestUtils.setField(unconfigured, "secretKeyString", "");
        assertFalse(unconfigured.validateToken("any-token"));
    }

    @Test
    void extractAllClaims_returnsCorrectClaims() {
        String token = jwtUtil.generateToken("user@test.com", "ADMIN", 42L, "Admin User");
        Claims claims = jwtUtil.extractAllClaims(token);

        assertEquals("user@test.com", claims.getSubject());
        assertEquals("ADMIN", claims.get("role", String.class));
        assertEquals(42, claims.get("userId", Integer.class));
        assertEquals("Admin User", claims.get("name", String.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void extractAllClaims_throwsWhenSecretNotConfigured() {
        JwtUtil unconfigured = new JwtUtil();
        ReflectionTestUtils.setField(unconfigured, "secretKeyString", "short");
        assertThrows(IllegalStateException.class, () ->
                unconfigured.extractAllClaims("any-token"));
    }

    @Test
    void generateToken_andValidate_roundTrip() {
        String token = jwtUtil.generateToken("instructor@edu.com", "INSTRUCTOR", 10L, "Prof");
        assertTrue(jwtUtil.validateToken(token));

        Claims claims = jwtUtil.extractAllClaims(token);
        assertEquals("instructor@edu.com", claims.getSubject());
        assertEquals("INSTRUCTOR", claims.get("role", String.class));
    }

    @Test
    void validateToken_returnsFalseForNullToken() {
        assertFalse(jwtUtil.validateToken(null));
    }

    @Test
    void validateToken_returnsFalseForEmptyToken() {
        assertFalse(jwtUtil.validateToken(""));
    }
}
