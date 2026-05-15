package com.olp.auth.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.auth.model.AuthUser;
import com.olp.auth.repository.AuthUserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final AuthUserRepository authUserRepository;
    private final JwtUtil jwtUtil;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public OAuth2LoginSuccessHandler(AuthUserRepository authUserRepository, JwtUtil jwtUtil, KafkaTemplate<String, String> kafkaTemplate) {
        this.authUserRepository = authUserRepository;
        this.jwtUtil = jwtUtil;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");
        if (email == null || email.isBlank()) {
            log.warn("Google OAuth callback received without email claim");
            response.sendRedirect(frontendUrl + "/login?oauthError=" + URLEncoder.encode("Google account did not return an email address", StandardCharsets.UTF_8));
            return;
        }
        String normalizedEmail = email.trim().toLowerCase();

        AuthUser user = authUserRepository.findByEmail(normalizedEmail).orElseGet(AuthUser::new);
        
        if (user.getId() == null) {
            user.setEmail(normalizedEmail);
            user.setName(name != null ? name : "Google User");
            user.setProvider("GOOGLE");
            user.setRole("STUDENT");
            user.setAvatarUrl(picture);
            user = authUserRepository.save(user);
            publishUserSignupEvent(user);
        } else {
            boolean changed = false;
            boolean avatarChanged = false;
            if (user.getEmail() == null || !normalizedEmail.equalsIgnoreCase(user.getEmail())) {
                user.setEmail(normalizedEmail);
                changed = true;
            }
            if (user.getName() == null || user.getName().isBlank()) {
                user.setName(name != null ? name : "Google User");
                changed = true;
            }
            if (user.getProvider() == null || user.getProvider().isBlank()) {
                user.setProvider("GOOGLE");
                changed = true;
            }
            if (picture != null && !picture.equals(user.getAvatarUrl())) {
                user.setAvatarUrl(picture);
                changed = true;
                avatarChanged = true;
            }
            if (changed) {
                user = authUserRepository.save(user);
            }
            if (avatarChanged) {
                publishUserAvatarUpdatedEvent(user);
            }
        }

        publishUserLoginEvent(user);
        log.info("OAuth login success userId={} email={} role={}", user.getId(), user.getEmail(), user.getRole());
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getId(), user.getName());

        // Pass only individual fields as URL params — avoids JSON URL-encoding issues
        String redirectUrl = frontendUrl + "/login" +
                "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8) +
                "&userId=" + user.getId() +
                "&email=" + URLEncoder.encode(user.getEmail() != null ? user.getEmail() : "", StandardCharsets.UTF_8) +
                "&name=" + URLEncoder.encode(user.getName() != null ? user.getName() : "", StandardCharsets.UTF_8) +
                "&role=" + URLEncoder.encode(user.getRole() != null ? user.getRole() : "STUDENT", StandardCharsets.UTF_8) +
                "&avatarUrl=" + URLEncoder.encode(user.getAvatarUrl() != null ? user.getAvatarUrl() : "", StandardCharsets.UTF_8);

        response.sendRedirect(redirectUrl);
    }

    private void publishUserSignupEvent(AuthUser user) {
        publishUserEvent("USER_SIGNUP", user);
    }

    private void publishUserAvatarUpdatedEvent(AuthUser user) {
        publishUserEvent("USER_AVATAR_UPDATED", user);
    }

    private void publishUserLoginEvent(AuthUser user) {
        publishUserEvent("USER_LOGIN", user);
    }

    private void publishUserEvent(String eventType, AuthUser user) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            event.put("userId", user.getId());
            event.put("email", user.getEmail());
            event.put("fullName", user.getName());
            event.put("role", user.getRole());
            event.put("avatarUrl", user.getAvatarUrl());
            kafkaTemplate.send("user-events", String.valueOf(user.getId()), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("Failed to publish {} event for userId={}", eventType, user.getId(), e);
        }
    }
}
