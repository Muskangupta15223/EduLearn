package com.olp.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.auth.config.JwtUtil;
import com.olp.auth.constant.AuthConstants;
import com.olp.auth.dto.AuthDtos.*;
import com.olp.auth.model.AuthUser;
import com.olp.auth.repository.AuthUserRepository;
// import java.security.Principal;
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
  public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
      String email = normalizeEmail(request.email());
      String password = request.password();

      if (email == null || email.isBlank() || password == null || password.isBlank()) {
          return ResponseEntity.badRequest().body(new ErrorResponse("Email and password are required"));
      }

      if (authUserRepository.findByEmail(email).isPresent()) {
          return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("Email already exists"));
      }

      AuthUser user = new AuthUser();
      user.setEmail(email);
      user.setName(request.name());
      user.setPassword(passwordEncoder.encode(password));
      user.setProvider(AuthConstants.PROVIDER_LOCAL);
      user.setRole(resolveRegistrationRole(request.role()));
      AuthUser saved = authUserRepository.save(user);

      publishUserSignupEvent(saved);

      String token = jwtUtil.generateToken(saved.getEmail(), saved.getRole(), saved.getId(), saved.getName());
      return ResponseEntity.ok(new AuthResponse(token, saved));
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginRequest request) {
      String email = normalizeEmail(request.email());
      String password = request.password();
      
      if (email == null || email.isBlank() || password == null || password.isBlank()) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(AuthConstants.MSG_INVALID_CREDENTIALS));
      }
      log.debug("Login attempt received for {}", email);

      if (isBootstrapAdminLogin(email, password)) {
          return handleBootstrapAdminLogin();
      }

      return handleNormalLogin(email, password);
  }

  private ResponseEntity<?> handleBootstrapAdminLogin() {
      AuthUser admin = authUserRepository.findByEmail(normalizeEmail(bootstrapAdminEmail))
              .orElseGet(AuthUser::new);
      admin.setEmail(normalizeEmail(bootstrapAdminEmail));
      admin.setName(admin.getName() == null || admin.getName().isBlank() ? bootstrapAdminName : admin.getName());
      admin.setPassword(passwordEncoder.encode(bootstrapAdminPassword));
      admin.setProvider(admin.getProvider() == null || admin.getProvider().isBlank() ? AuthConstants.PROVIDER_LOCAL : admin.getProvider());
      admin.setRole(AuthConstants.ROLE_ADMIN);
      AuthUser saved = authUserRepository.save(admin);
      publishUserSignupEvent(saved);
      publishUserLoginEvent(saved);
      String token = jwtUtil.generateToken(saved.getEmail(), saved.getRole(), saved.getId(), saved.getName());
      return ResponseEntity.ok(new AuthResponse(token, saved));
  }

  private ResponseEntity<?> handleNormalLogin(String email, String password) {
      AuthUser user = authUserRepository.findByEmail(email).orElse(null);
      if (user == null || user.getPassword() == null) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(AuthConstants.MSG_INVALID_CREDENTIALS));
      }
      if (AuthConstants.ROLE_DEACTIVATED.equalsIgnoreCase(user.getRole())) {
          log.warn("Blocked login attempt for deactivated account {}", email);
          return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(AuthConstants.MSG_ACCOUNT_DEACTIVATED));
      }

      if (!checkAndUpdatePassword(user, password)) {
          log.warn("Login failed due to invalid password for {}", email);
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(AuthConstants.MSG_INVALID_CREDENTIALS));
      }

      publishUserLoginEvent(user);
      log.info("Login success for userId={} email={} role={}", user.getId(), user.getEmail(), user.getRole());
      String token = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getId(), user.getName());
      return ResponseEntity.ok(new AuthResponse(token, user));
  }

  private boolean checkAndUpdatePassword(AuthUser user, String password) {
      boolean isPlaintext = !user.getPassword().startsWith("$2a$");

      if (isPlaintext) {
          if (password.equals(user.getPassword())) {
              user.setPassword(passwordEncoder.encode(password));
              authUserRepository.save(user);
              return true;
          }
          return false;
      }
      return passwordEncoder.matches(password, user.getPassword());
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

  @PostMapping("/forgot-password")
  public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
      String email = normalizeEmail(request.email());
      AuthUser user = authUserRepository.findByEmail(email).orElse(null);
      if (user == null) {
          return ResponseEntity.ok(new MessageResponse("If the email is registered, you will receive a reset link."));
      }

      String resetToken = UUID.randomUUID().toString();
      purgeExpiredResetTokens();
      resetTokens.put(resetToken, new ResetTokenRecord(email, System.currentTimeMillis() + RESET_TOKEN_TTL_MILLIS));

      try {
          String resetLink = frontendUrl.replaceAll("/+$", "") + "/reset-password?token=" + resetToken;
          String event = String.format(
              "{\"eventType\":\"%s\",\"email\":\"%s\",\"fullName\":\"%s\",\"resetLink\":\"%s\"}",
              AuthConstants.EVENT_PASSWORD_RESET, email, user.getName(), resetLink
          );
          kafkaTemplate.send("user-events", event);
      } catch (Exception e) {
          log.warn("Failed to publish PASSWORD_RESET event for {}", email, e);
      }

      return ResponseEntity.ok(new MessageResponse("If the email is registered, you will receive a reset link."));
  }

  @PostMapping("/reset-password")
  public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
      String token = request.token();
      String newPassword = request.password();

      if (token == null || newPassword == null) {
          return ResponseEntity.badRequest().body(new ErrorResponse("Token and password are required"));
      }

      purgeExpiredResetTokens();
      ResetTokenRecord tokenRecord = resetTokens.get(token);
      if (tokenRecord == null || tokenRecord.expiresAt() < System.currentTimeMillis()) {
          resetTokens.remove(token);
          return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("Invalid or expired reset token"));
      }

      String email = tokenRecord.email();
      AuthUser user = authUserRepository.findByEmail(email).orElse(null);
      if (user == null) {
          return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(AuthConstants.MSG_USER_NOT_FOUND));
      }

      user.setPassword(passwordEncoder.encode(newPassword));
      authUserRepository.save(user);
      resetTokens.remove(token);

      return ResponseEntity.ok(new MessageResponse("Password reset successfully. You can now log in."));
  }

  @GetMapping("/login/success")
  public ResponseEntity<?> loginSuccess(@AuthenticationPrincipal OAuth2User oAuth2User) {
    if (oAuth2User == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    String email = normalizeEmail(oAuth2User.getAttribute(AuthConstants.KEY_EMAIL));
    String name = oAuth2User.getAttribute("name");
    String picture = oAuth2User.getAttribute("picture");

    AuthUser user = authUserRepository.findByEmail(email).orElseGet(AuthUser::new);
    if (AuthConstants.ROLE_DEACTIVATED.equalsIgnoreCase(user.getRole())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(AuthConstants.MSG_ACCOUNT_DEACTIVATED));
    }
    if (user.getId() == null) {
        user.setEmail(email);
        user.setName(name != null ? name : "Google User");
        user.setProvider(AuthConstants.PROVIDER_GOOGLE);
        user.setRole(AuthConstants.ROLE_STUDENT);
        user.setAvatarUrl(picture);
        user = authUserRepository.save(user);

        publishUserSignupEvent(user);
    } else if (picture != null && !picture.equals(user.getAvatarUrl())) {
        user.setAvatarUrl(picture);
        user = authUserRepository.save(user);
        publishUserAvatarUpdatedEvent(user);
    }

    publishUserLoginEvent(user);
    log.info("OAuth login success for userId={} email={} role={}", user.getId(), user.getEmail(), user.getRole());
    String token = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getId(), user.getName());

    Map<String, Object> response = new HashMap<>();
    response.put(AuthConstants.KEY_MESSAGE, "Google OAuth login successful");
    response.put(AuthConstants.KEY_TOKEN, token);
    response.put("user", user);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/me")
  public ResponseEntity<?> me(@RequestHeader(value = "X-User-Email", required = false) String email) {
    if (email == null || email.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    return authUserRepository.findByEmail(email)
        .<ResponseEntity<?>>map(user -> {
            if (AuthConstants.ROLE_DEACTIVATED.equalsIgnoreCase(user.getRole())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(AuthConstants.MSG_ACCOUNT_DEACTIVATED_SHORT));
            }
            return ResponseEntity.ok(ProfileResponse.from(user));
        })
        .orElseGet(() -> {
            log.warn("/auth/me — user not found in DB for email={}", email);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(AuthConstants.MSG_USER_NOT_FOUND));
        });
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(@RequestHeader(value = "X-User-Email", required = false) String email) {
      if (email == null || email.isBlank()) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(AuthConstants.MSG_MISSING_AUTH_USER));
      }
      AuthUser user = authUserRepository.findByEmail(normalizeEmail(email)).orElse(null);
      if (user == null) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(AuthConstants.MSG_USER_NOT_FOUND));
      }
      if (AuthConstants.ROLE_DEACTIVATED.equalsIgnoreCase(user.getRole())) {
          return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(AuthConstants.MSG_ACCOUNT_DEACTIVATED_SHORT));
      }
      String token = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getId(), user.getName());
      return ResponseEntity.ok(new AuthResponse(token, user));
  }

  @PostMapping("/validate")
  public ResponseEntity<?> validate(@RequestBody TokenRequest request) {
      String token = request.token();
      if (token == null || token.isBlank()) {
          return ResponseEntity.badRequest().body(new ErrorResponse("Token is required"));
      }
      if (!jwtUtil.validateToken(token)) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new TokenValidationResponse(false, null, null, null, null, null));
      }
      Claims claims = jwtUtil.extractAllClaims(token);
      return ResponseEntity.ok(new TokenValidationResponse(
              true,
              claims.getSubject(),
              claims.get("role", String.class),
              claims.get("userId"),
              claims.get("name", String.class),
              claims.getExpiration()
      ));
  }

  @PutMapping("/password")
  public ResponseEntity<?> changePassword(
          @RequestHeader(value = "X-User-Email", required = false) String email,
          @RequestBody ChangePasswordRequest request) {
      if (email == null || email.isBlank()) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(AuthConstants.MSG_MISSING_AUTH_USER));
      }
      String currentPassword = request.currentPassword();
      String newPassword = request.newPassword();
      if (newPassword == null || newPassword.isBlank()) {
          return ResponseEntity.badRequest().body(new ErrorResponse("New password is required"));
      }
      AuthUser user = authUserRepository.findByEmail(normalizeEmail(email)).orElse(null);
      if (user == null) {
          return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(AuthConstants.MSG_USER_NOT_FOUND));
      }
      if (user.getPassword() != null && !user.getPassword().isBlank()) {
          boolean matches = user.getPassword().startsWith("$2")
                  ? passwordEncoder.matches(currentPassword == null ? "" : currentPassword, user.getPassword())
                  : user.getPassword().equals(currentPassword);
          if (!matches) {
              return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Current password is incorrect"));
          }
      }
      user.setPassword(passwordEncoder.encode(newPassword));
      authUserRepository.save(user);
      return ResponseEntity.ok(new MessageResponse("Password updated successfully"));
  }

  @PutMapping("/profile")
  public ResponseEntity<?> updateProfile(
          @RequestHeader(value = "X-User-Email", required = false) String email,
          @RequestBody ProfileUpdateRequest request) {
      if (email == null || email.isBlank()) {
          return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(AuthConstants.MSG_MISSING_AUTH_USER));
      }
      AuthUser user = authUserRepository.findByEmail(normalizeEmail(email)).orElse(null);
      if (user == null) {
          return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(AuthConstants.MSG_USER_NOT_FOUND));
      }
      if (request.name() != null) user.setName(request.name());
      if (request.avatarUrl() != null) user.setAvatarUrl(request.avatarUrl());
      if (request.mobile() != null) user.setMobile(request.mobile());
      if (request.bio() != null) user.setBio(request.bio());
      
      authUserRepository.save(user);
      publishUserEvent(AuthConstants.EVENT_USER_PROFILE_UPDATED, user);
      
      return ResponseEntity.ok(ProfileResponse.from(user));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout() {
    return ResponseEntity.ok(new MessageResponse("Logged out"));
  }

  @GetMapping("/public/ping")
  public String ping() {
    return "auth-service-up";
  }

  private void publishUserSignupEvent(AuthUser user) {
      publishUserEvent(AuthConstants.EVENT_USER_SIGNUP, user);
  }

  private void publishUserAvatarUpdatedEvent(AuthUser user) {
      publishUserEvent(AuthConstants.EVENT_USER_AVATAR_UPDATED, user);
  }

  private void publishUserLoginEvent(AuthUser user) {
      publishUserEvent(AuthConstants.EVENT_USER_LOGIN, user);
  }

  private void publishUserEvent(String eventType, AuthUser user) {
      try {
          Map<String, Object> event = new HashMap<>();
          event.put("eventType", eventType);
          event.put("userId", user.getId());
          event.put(AuthConstants.KEY_EMAIL, user.getEmail());
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
      if (AuthConstants.ROLE_INSTRUCTOR.equalsIgnoreCase(requestedRole)) {
          return AuthConstants.ROLE_INSTRUCTOR;
      }
      return AuthConstants.ROLE_STUDENT;
  }

  private void purgeExpiredResetTokens() {
      long now = System.currentTimeMillis();
      resetTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
  }

  private record ResetTokenRecord(String email, long expiresAt) {}
}
