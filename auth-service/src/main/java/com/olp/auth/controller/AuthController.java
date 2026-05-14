package com.olp.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.auth.config.JwtUtil;
import com.olp.auth.model.AuthUser;
import com.olp.auth.repository.AuthUserRepository;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
  private static final Logger log = LoggerFactory.getLogger(AuthController.class);
  private static final long RESET_TOKEN_TTL_MILLIS = TimeUnit.HOURS.toMillis(1);

  private final AuthUserRepository authUserRepository;
  private final JwtUtil jwtUtil;
  private final PasswordEncoder passwordEncoder;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${app.frontend-url:http://localhost:3000}")
  private String frontendUrl = "http://localhost:3000";

  @Value("${app.bootstrap-admin.enabled:false}")
  private boolean bootstrapAdminEnabled;

  @Value("${app.bootstrap-admin.email:}")
  private String bootstrapAdminEmail = "";

  @Value("${app.bootstrap-admin.password:}")
  private String bootstrapAdminPassword = "";

  @Value("${app.bootstrap-admin.name:Admin}")
  private String bootstrapAdminName = "Admin";

  private final ConcurrentHashMap<String, ResetTokenRecord> resetTokens = new ConcurrentHashMap<>();

  public AuthController(AuthUserRepository authUserRepository, JwtUtil jwtUtil,
                         PasswordEncoder passwordEncoder, KafkaTemplate<String, String> kafkaTemplate) {
    this.authUserRepository = authUserRepository;
    this.jwtUtil = jwtUtil;
    this.passwordEncoder = passwordEncoder;
    this.kafkaTemplate = kafkaTemplate;
  }

  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
      String email = normalizeEmail(body.get("email"));
      String name = body.get("name");
      String password = body.get("password");
      String role = resolveRegistrationRole(body.get("role"));

      if (email == null || email.isBlank() || password == null || password.isBlank()) {
          return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required"));
      }

      if (authUserRepository.findByEmail(email).isPresent()) {
          return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Email already exists"));
      }

      AuthUser user = new AuthUser();
      user.setEmail(email);
      user.setName(name);
      user.setPassword(passwordEncoder.encode(password));
      user.setProvider("LOCAL");
      user.setRole(role);
      AuthUser saved = authUserRepository.save(user);

      // Publish USER_SIGNUP event to Kafka (async, non-blocking)
      publishUserSignupEvent(saved);

      String token = jwtUtil.generateToken(saved.getEmail(), saved.getRole(), saved.getId(), saved.getName());
      return ResponseEntity.ok(Map.of("token", token, "user", saved));
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
      String email = normalizeEmail(body.get("email"));
      String password = body.get("password");
      if (email == null || email.isBlank() || password == null || password.isBlank()) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
      }
      log.debug("Login attempt received for {}", email);

      if (isBootstrapAdminLogin(email, password)) {
          AuthUser admin = authUserRepository.findByEmail(normalizeEmail(bootstrapAdminEmail))
                  .orElseGet(AuthUser::new);
          admin.setEmail(normalizeEmail(bootstrapAdminEmail));
          admin.setName(admin.getName() == null || admin.getName().isBlank() ? bootstrapAdminName : admin.getName());
          admin.setPassword(passwordEncoder.encode(bootstrapAdminPassword));
          admin.setProvider(admin.getProvider() == null || admin.getProvider().isBlank() ? "LOCAL" : admin.getProvider());
          admin.setRole("ADMIN");
          AuthUser saved = authUserRepository.save(admin);
          publishUserSignupEvent(saved);
          publishUserLoginEvent(saved);
          String token = jwtUtil.generateToken(saved.getEmail(), saved.getRole(), saved.getId(), saved.getName());
          return ResponseEntity.ok(Map.of("token", token, "user", saved));
      }

      AuthUser user = authUserRepository.findByEmail(email).orElse(null);
      if (user == null || user.getPassword() == null) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
      }
      if ("DEACTIVATED".equalsIgnoreCase(user.getRole())) {
          log.warn("Blocked login attempt for deactivated account {}", email);
          return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Your account has been deactivated. Contact support."));
      }

      boolean isPasswordMatch = false;
      boolean isPlaintext = !user.getPassword().startsWith("$2a$");

      if (isPlaintext) {
          if (password.equals(user.getPassword())) {
              isPasswordMatch = true;
              // Seamless migration: hash and save
              user.setPassword(passwordEncoder.encode(password));
              authUserRepository.save(user);
          }
      } else {
          isPasswordMatch = passwordEncoder.matches(password, user.getPassword());
      }

      if (!isPasswordMatch) {
          log.warn("Login failed due to invalid password for {}", email);
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
      }

      publishUserLoginEvent(user);
      log.info("Login success for userId={} email={} role={}", user.getId(), user.getEmail(), user.getRole());
      String token = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getId(), user.getName());
      return ResponseEntity.ok(Map.of("token", token, "user", user));
  }

  private boolean isBootstrapAdminLogin(String email, String password) {
      String normalizedBootstrapEmail = normalizeEmail(bootstrapAdminEmail);
      return bootstrapAdminEnabled
              && normalizedBootstrapEmail != null
              && !normalizedBootstrapEmail.isBlank()
              && bootstrapAdminPassword != null
              && !bootstrapAdminPassword.isBlank()
              && normalizedBootstrapEmail.equalsIgnoreCase(email == null ? "" : email.trim())
              && bootstrapAdminPassword.equals(password);
  }

  // ── Forgot Password: generate a reset token and publish event ──
  @PostMapping("/forgot-password")
  public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
      String email = normalizeEmail(body.get("email"));
      AuthUser user = authUserRepository.findByEmail(email).orElse(null);
      if (user == null) {
          // Don't reveal whether the email exists
          return ResponseEntity.ok(Map.of("message", "If the email is registered, you will receive a reset link."));
      }

      String resetToken = UUID.randomUUID().toString();
      purgeExpiredResetTokens();
      resetTokens.put(resetToken, new ResetTokenRecord(email, System.currentTimeMillis() + RESET_TOKEN_TTL_MILLIS));

      // Publish event so notification-service can send the email
      try {
          String resetLink = frontendUrl.replaceAll("/+$", "") + "/reset-password?token=" + resetToken;
          String event = String.format(
              "{\"eventType\":\"PASSWORD_RESET\",\"email\":\"%s\",\"fullName\":\"%s\",\"resetLink\":\"%s\"}",
              email, user.getName(), resetLink
          );
          kafkaTemplate.send("user-events", event);
      } catch (Exception e) {
          log.warn("Failed to publish PASSWORD_RESET event for {}", email, e);
      }

      return ResponseEntity.ok(Map.of("message", "If the email is registered, you will receive a reset link."));
  }

  // ── Reset Password: validate token and update password ──
  @PostMapping("/reset-password")
  public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
      String token = body.get("token");
      String newPassword = body.get("password");

      if (token == null || newPassword == null) {
          return ResponseEntity.badRequest().body(Map.of("error", "Token and password are required"));
      }

      purgeExpiredResetTokens();
      ResetTokenRecord tokenRecord = resetTokens.get(token);
      if (tokenRecord == null || tokenRecord.expiresAt() < System.currentTimeMillis()) {
          resetTokens.remove(token);
          return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid or expired reset token"));
      }

      String email = tokenRecord.email();
      AuthUser user = authUserRepository.findByEmail(email).orElse(null);
      if (user == null) {
          return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "User not found"));
      }

      user.setPassword(passwordEncoder.encode(newPassword));
      authUserRepository.save(user);
      resetTokens.remove(token); // Invalidate the token

      return ResponseEntity.ok(Map.of("message", "Password reset successfully. You can now log in."));
  }

  @GetMapping("/login/success")
  public ResponseEntity<Map<String, Object>> loginSuccess(
    @AuthenticationPrincipal OAuth2User oAuth2User
  ) {
    if (oAuth2User == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    String email = normalizeEmail(oAuth2User.getAttribute("email"));
    String name = oAuth2User.getAttribute("name");
    String picture = oAuth2User.getAttribute("picture");

    AuthUser user = authUserRepository
      .findByEmail(email)
      .orElseGet(AuthUser::new);
    if ("DEACTIVATED".equalsIgnoreCase(user.getRole())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Your account has been deactivated. Contact support."));
    }
    if (user.getId() == null) {
        user.setEmail(email);
        user.setName(name != null ? name : "Google User");
        user.setProvider("GOOGLE");
        user.setRole("STUDENT");
        user.setAvatarUrl(picture);
        user = authUserRepository.save(user);

        // Publish signup event for Google users too
        publishUserSignupEvent(user);
    } else if (picture != null && !picture.equals(user.getAvatarUrl())) {
        user.setAvatarUrl(picture);
        user = authUserRepository.save(user);
        publishUserAvatarUpdatedEvent(user);
    }

    publishUserLoginEvent(user);
    log.info("OAuth login success for userId={} email={} role={}", user.getId(), user.getEmail(), user.getRole());
    String token = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getId(), user.getName());

    return ResponseEntity.ok(
      Map.of(
        "message", "Google OAuth login successful",
        "token", token,
        "user", user
      )
    );
  }

  @GetMapping("/me")
  public ResponseEntity<?> me(@RequestHeader(value = "X-User-Email", required = false) String email) {
    if (email == null || email.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    return authUserRepository.findByEmail(email)
        .<ResponseEntity<?>>map(user -> {
            if ("DEACTIVATED".equalsIgnoreCase(user.getRole())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Your account has been deactivated."));
            }
            Map<String, Object> response = new HashMap<>();
            response.put("id", user.getId());
            response.put("email", user.getEmail());
            response.put("name", user.getName());
            response.put("role", user.getRole());
            response.put("provider", user.getProvider());
            response.put("avatarUrl", user.getAvatarUrl());
            response.put("mobile", user.getMobile());
            response.put("bio", user.getBio());
            response.put("createdAt", user.getCreatedAt());
            return ResponseEntity.ok(response);
        })
        .orElseGet(() -> {
            log.warn("/auth/me — user not found in DB for email={}", email);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not found"));
        });
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(@RequestHeader(value = "X-User-Email", required = false) String email) {
      if (email == null || email.isBlank()) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Missing authenticated user"));
      }
      AuthUser user = authUserRepository.findByEmail(normalizeEmail(email)).orElse(null);
      if (user == null) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
      }
      if ("DEACTIVATED".equalsIgnoreCase(user.getRole())) {
          return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Your account has been deactivated."));
      }
      String token = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getId(), user.getName());
      return ResponseEntity.ok(Map.of("token", token, "user", user));
  }

  @PostMapping("/validate")
  public ResponseEntity<?> validate(@RequestBody Map<String, String> body) {
      String token = body.get("token");
      if (token == null || token.isBlank()) {
          return ResponseEntity.badRequest().body(Map.of("error", "Token is required"));
      }
      if (!jwtUtil.validateToken(token)) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("valid", false));
      }
      Claims claims = jwtUtil.extractAllClaims(token);
      return ResponseEntity.ok(Map.of(
              "valid", true,
              "email", claims.getSubject(),
              "role", claims.get("role", String.class),
              "userId", claims.get("userId"),
              "name", claims.get("name", String.class),
              "expiresAt", claims.getExpiration()
      ));
  }

  @PutMapping("/password")
  public ResponseEntity<?> changePassword(
          @RequestHeader(value = "X-User-Email", required = false) String email,
          @RequestBody Map<String, String> body) {
      if (email == null || email.isBlank()) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Missing authenticated user"));
      }
      String currentPassword = body.get("currentPassword");
      String newPassword = body.get("newPassword");
      if (newPassword == null || newPassword.isBlank()) {
          return ResponseEntity.badRequest().body(Map.of("error", "New password is required"));
      }
      AuthUser user = authUserRepository.findByEmail(normalizeEmail(email)).orElse(null);
      if (user == null) {
          return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
      }
      if (user.getPassword() != null && !user.getPassword().isBlank()) {
          boolean matches = user.getPassword().startsWith("$2")
                  ? passwordEncoder.matches(currentPassword == null ? "" : currentPassword, user.getPassword())
                  : user.getPassword().equals(currentPassword);
          if (!matches) {
              return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Current password is incorrect"));
          }
      }
      user.setPassword(passwordEncoder.encode(newPassword));
      authUserRepository.save(user);
      return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
  }

  @PutMapping("/profile")
  public ResponseEntity<?> updateProfile(
          @RequestHeader(value = "X-User-Email", required = false) String email,
          @RequestBody Map<String, String> body) {
      if (email == null || email.isBlank()) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Missing authenticated user"));
      }
      AuthUser user = authUserRepository.findByEmail(normalizeEmail(email)).orElse(null);
      if (user == null) {
          return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
      }
      if (body.containsKey("name")) user.setName(body.get("name"));
      if (body.containsKey("avatarUrl")) user.setAvatarUrl(body.get("avatarUrl"));
      if (body.containsKey("mobile")) user.setMobile(body.get("mobile"));
      if (body.containsKey("bio")) user.setBio(body.get("bio"));
      authUserRepository.save(user);
      publishUserEvent("USER_PROFILE_UPDATED", user);
      Map<String, Object> response = new HashMap<>();
      response.put("id", user.getId());
      response.put("email", user.getEmail());
      response.put("name", user.getName());
      response.put("role", user.getRole());
      response.put("provider", user.getProvider());
      response.put("avatarUrl", user.getAvatarUrl());
      response.put("mobile", user.getMobile());
      response.put("bio", user.getBio());
      return ResponseEntity.ok(response);
  }

  @PostMapping("/logout")
  public ResponseEntity<Map<String, String>> logout() {
    return ResponseEntity.ok(Map.of("message", "Logged out"));
  }

  @GetMapping("/public/ping")
  public String ping() {
    return "auth-service-up";
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

  private String normalizeEmail(String email) {
      if (email == null) return null;
      return email.trim().toLowerCase();
  }

  private String resolveRegistrationRole(String requestedRole) {
      if ("INSTRUCTOR".equalsIgnoreCase(requestedRole)) {
          return "INSTRUCTOR";
      }
      return "STUDENT";
  }

  private void purgeExpiredResetTokens() {
      long now = System.currentTimeMillis();
      resetTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
  }

  private record ResetTokenRecord(String email, long expiresAt) {}
}
