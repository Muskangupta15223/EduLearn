package com.olp.auth.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {
    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    private static final long EXPIRATION_TIME = 86400000; // 24 hours

    @Value("${jwt.secret:${JWT_SECRET:}}")
    private String secretKeyString = "";

    public String generateToken(String email, String role, Long userId, String name) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("userId", userId);
        claims.put("name", name);
        return createToken(claims, email);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(secretKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        if (!isSecretConfigured()) {
            log.error("JWT validation rejected because jwt.secret/JWT_SECRET is not configured");
            return false;
        }
        try {
            Jwts.parserBuilder().setSigningKey(secretKey()).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Claims extractAllClaims(String token) {
        ensureSecretConfigured();
        return Jwts.parserBuilder().setSigningKey(secretKey()).build().parseClaimsJws(token).getBody();
    }

    private Key secretKey() {
        ensureSecretConfigured();
        return Keys.hmacShaKeyFor(secretKeyString.getBytes());
    }

    private boolean isSecretConfigured() {
        return secretKeyString != null && !secretKeyString.isBlank() && secretKeyString.length() >= 32;
    }

    private void ensureSecretConfigured() {
        if (!isSecretConfigured()) {
            throw new IllegalStateException("JWT secret must be configured and at least 32 characters long");
        }
    }
}
