package com.olp.user.controller;

import com.olp.user.model.UserProfile;
import com.olp.user.service.UserService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.olp.user.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/users")
public class UserController {

  private final UserService userService;
  private final FileStorageService fileStorageService;

  public UserController(UserService userService, FileStorageService fileStorageService) {
    this.userService = userService;
    this.fileStorageService = fileStorageService;
  }

  @PostMapping
  public ResponseEntity<UserProfile> create(@RequestBody UserProfile profile) {
    return ResponseEntity.ok(userService.createUser(profile));
  }

  @GetMapping
  public ResponseEntity<List<UserProfile>> all(
      @RequestParam(value = "role", required = false) String role,
      @RequestHeader(value = "X-User-Role", required = false) String actorRole
  ) {
    requireAdmin(actorRole);
    if (role != null && !role.isBlank()) {
      return ResponseEntity.ok(userService.getUsersByRole(role));
    }
    return ResponseEntity.ok(userService.getAllUsers());
  }

  @GetMapping("/{id}")
  public ResponseEntity<UserProfile> getById(@PathVariable Long id) {
    return userService.getUserById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PutMapping("/{id}/role")
  public ResponseEntity<UserProfile> updateRole(
    @PathVariable Long id,
    @RequestBody Map<String, String> body,
    @RequestHeader(value = "X-User-Role", required = false) String actorRole
  ) {
    requireAdmin(actorRole);
    return userService.updateUserRole(id, body.get("role"))
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PutMapping("/{id}/status")
  public ResponseEntity<UserProfile> updateStatus(
      @PathVariable Long id,
      @RequestBody Map<String, String> body,
      @RequestHeader(value = "X-User-Role", required = false) String actorRole
  ) {
    requireAdmin(actorRole);
    String status = body.get("status");
    return userService.getUserById(id)
        .map(user -> {
          UserProfile patch = new UserProfile();
          patch.setAccountStatus(status);
          if ("SUSPENDED".equalsIgnoreCase(status)) {
            patch.setRole("DEACTIVATED");
          } else if ("DEACTIVATED".equalsIgnoreCase(user.getRole())) {
            patch.setRole("STUDENT");
          }
          return ResponseEntity.ok(userService.updateUserProfile(id, patch).orElse(user));
        })
        .orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteUser(
      @PathVariable Long id,
      @RequestHeader(value = "X-User-Role", required = false) String actorRole
  ) {
    requireAdmin(actorRole);
    if (userService.deleteUser(id)) {
      return ResponseEntity.ok().build();
    }
    return ResponseEntity.notFound().build();
  }

  @GetMapping("/stats/count")
  public ResponseEntity<Long> getUserCount(
      @RequestHeader(value = "X-User-Role", required = false) String actorRole
  ) {
    requireAdmin(actorRole);
    return ResponseEntity.ok(userService.countUsers());
  }

  @GetMapping("/{id}/profile")
  public ResponseEntity<UserProfile> getProfile(
      @PathVariable Long id,
      @RequestHeader(value = "X-User-Id", required = false) Long actorUserId,
      @RequestHeader(value = "X-User-Role", required = false) String actorRole
  ) {
      requireSelfOrAdmin(id, actorUserId, actorRole);
      return userService.getUserById(id)
              .map(ResponseEntity::ok)
              .orElse(ResponseEntity.notFound().build());
  }

  @PutMapping("/{id}/profile")
  public ResponseEntity<UserProfile> updateProfile(
      @PathVariable Long id,
      @RequestBody UserProfile profile,
      @RequestHeader(value = "X-User-Id", required = false) Long actorUserId,
      @RequestHeader(value = "X-User-Role", required = false) String actorRole
  ) {
      requireSelfOrAdmin(id, actorUserId, actorRole);
      return userService.updateUserProfile(id, profile)
              .map(ResponseEntity::ok)
              .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/{id}/avatar")
  public ResponseEntity<UserProfile> uploadAvatar(
      @PathVariable Long id,
      @RequestParam("file") MultipartFile file,
      @RequestHeader(value = "X-User-Id", required = false) Long actorUserId,
      @RequestHeader(value = "X-User-Role", required = false) String actorRole
  ) {
      requireSelfOrAdmin(id, actorUserId, actorRole);
      String fileName = fileStorageService.storeAvatar(file);
      
      // Use a relative path so it works through any gateway/proxy
      String fileDownloadUri = "/users/uploads/" + fileName;

      UserProfile profileUpdate = new UserProfile();
      profileUpdate.setAvatarUrl(fileDownloadUri);

      return userService.updateUserProfile(id, profileUpdate)
              .map(ResponseEntity::ok)
              .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/instructors/verification-requests")
  public ResponseEntity<List<UserProfile>> pendingInstructorVerifications(
      @RequestHeader(value = "X-User-Role", required = false) String role
  ) {
      requireAdmin(role);
      return ResponseEntity.ok(userService.getPendingInstructorVerifications());
  }

  @PostMapping("/{id}/verification")
  public ResponseEntity<UserProfile> submitInstructorVerification(
      @PathVariable Long id,
      @RequestParam("file") MultipartFile file,
      @RequestHeader(value = "X-User-Id", required = false) Long actorUserId,
      @RequestHeader(value = "X-User-Role", required = false) String actorRole
  ) {
      String storedFilePath = fileStorageService.storeInstructorVerificationFile(file);
      return ResponseEntity.ok(userService.submitInstructorVerification(
          id,
          storedFilePath,
          file.getOriginalFilename(),
          actorUserId,
          actorRole
      ));
  }

  @PutMapping("/{id}/verification/review")
  public ResponseEntity<UserProfile> reviewInstructorVerification(
      @PathVariable Long id,
      @RequestBody Map<String, String> body,
      @RequestHeader(value = "X-User-Id", required = false) Long reviewerId,
      @RequestHeader(value = "X-User-Role", required = false) String reviewerRole
  ) {
      return ResponseEntity.ok(userService.reviewInstructorVerification(
          id,
          body.get("status"),
          body.get("comment"),
          reviewerId,
          reviewerRole
      ));
  }

  @GetMapping("/{id}/verification/document")
  public ResponseEntity<Resource> downloadVerificationDocument(
      @PathVariable Long id,
      @RequestHeader(value = "X-User-Id", required = false) Long actorUserId,
      @RequestHeader(value = "X-User-Role", required = false) String actorRole
  ) {
      UserProfile profile = userService.getVerificationProfileForDocument(id, actorUserId, actorRole);
      Resource resource = fileStorageService.loadVerificationFile(profile.getGovernmentIdFileUrl());
      String filename = profile.getGovernmentIdFileName() == null || profile.getGovernmentIdFileName().isBlank()
          ? "verification-document"
          : profile.getGovernmentIdFileName();

      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename.replace("\"", "") + "\"")
          .body(resource);
  }

  private void requireAdmin(String role) {
      if (role == null || !"ADMIN".equalsIgnoreCase(role)) {
          throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Admin access required");
      }
  }

  private void requireSelfOrAdmin(Long targetUserId, Long actorUserId, String actorRole) {
      if ("ADMIN".equalsIgnoreCase(actorRole)) {
          return;
      }
      if (actorUserId == null || !targetUserId.equals(actorUserId)) {
          throw new org.springframework.web.server.ResponseStatusException(
              org.springframework.http.HttpStatus.FORBIDDEN,
              "You can only access your own profile"
          );
      }
  }
}
