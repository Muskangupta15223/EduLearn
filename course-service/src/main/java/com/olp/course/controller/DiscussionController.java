package com.olp.course.controller;

import com.olp.course.model.DiscussionReply;
import com.olp.course.model.DiscussionThread;
import com.olp.course.service.DiscussionService;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/courses/{courseId}/discussions")
public class DiscussionController {

  private final DiscussionService discussionService;
  private final RestTemplate restTemplate;

  public DiscussionController(
    DiscussionService discussionService,
    RestTemplate restTemplate
  ) {
    this.discussionService = discussionService;
    this.restTemplate = restTemplate;
  }

  private boolean checkEnrollmentOrAccess(
    Long courseId,
    Long userId,
    String userRole
  ) {
    // Admins and Instructors bypass enrollment check
    if (
      "ADMIN".equalsIgnoreCase(userRole) ||
      "INSTRUCTOR".equalsIgnoreCase(userRole)
    ) {
      return true;
    }

    try {
      HttpHeaders headers = new HttpHeaders();
      headers.set("X-User-Id", String.valueOf(userId));
      HttpEntity<String> entity = new HttpEntity<>(headers);

      ResponseEntity<Map> response = restTemplate.exchange(
        "http://enrollment-service/enrollments/check/" + courseId,
        HttpMethod.GET,
        entity,
        Map.class
      );

      if (
        response.getStatusCode() == HttpStatus.OK && response.getBody() != null
      ) {
        Boolean enrolled = (Boolean) response.getBody().get("enrolled");
        return enrolled != null && enrolled;
      }
    } catch (Exception e) {
      System.err.println(
        "Failed to check enrollment for user " + userId + ": " + e.getMessage()
      );
    }
    return false; // Default deny if check fails
  }

  @PostMapping
  public ResponseEntity<?> createThread(
    @PathVariable Long courseId,
    @RequestBody DiscussionThread thread,
    @RequestHeader(value = "X-User-Id", required = false) Long userId,
    @RequestHeader(value = "X-User-Role", required = false) String userRole
  ) {
    if (userId == null) {
      return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", "Missing user id"));
    }
    if (!checkEnrollmentOrAccess(courseId, userId, userRole)) {
      return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(Map.of("error", "You must be enrolled to post discussions"));
    }

    thread.setUserId(userId);
    if (thread.getUserName() == null || thread.getUserName().isBlank()) {
      thread.setUserName("Student");
    }
    return ResponseEntity.ok(discussionService.createThread(courseId, thread));
  }

  @GetMapping
  public ResponseEntity<?> getThreads(
    @PathVariable Long courseId,
    @RequestHeader(value = "X-User-Id", required = false) Long userId,
    @RequestHeader(value = "X-User-Role", required = false) String userRole
  ) {
    if (
      userId == null &&
      !"ADMIN".equalsIgnoreCase(userRole) &&
      !"INSTRUCTOR".equalsIgnoreCase(userRole)
    ) {
      return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", "Missing user id"));
    }
    if (!checkEnrollmentOrAccess(courseId, userId, userRole)) {
      return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(Map.of("error", "You must be enrolled to view discussions"));
    }
    return ResponseEntity.ok(discussionService.getThreadsByCourse(courseId));
  }

  @GetMapping("/{threadId}")
  public ResponseEntity<?> getThread(
    @PathVariable Long courseId,
    @PathVariable Long threadId,
    @RequestHeader(value = "X-User-Id", required = false) Long userId,
    @RequestHeader(value = "X-User-Role", required = false) String userRole
  ) {
    if (
      userId == null &&
      !"ADMIN".equalsIgnoreCase(userRole) &&
      !"INSTRUCTOR".equalsIgnoreCase(userRole)
    ) {
      return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", "Missing user id"));
    }
    if (!checkEnrollmentOrAccess(courseId, userId, userRole)) {
      return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(Map.of("error", "You must be enrolled to view discussions"));
    }
    return discussionService
      .getThreadById(threadId)
      .map(ResponseEntity::ok)
      .orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{threadId}")
  public ResponseEntity<?> deleteThread(
    @PathVariable Long courseId,
    @PathVariable Long threadId,
    @RequestHeader(value = "X-User-Id", required = false) Long userId,
    @RequestHeader(value = "X-User-Role", required = false) String userRole
  ) {
    if (userId == null && !"ADMIN".equalsIgnoreCase(userRole)) {
      return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", "Missing user id"));
    }
    if (discussionService.deleteThread(threadId, userId, userRole)) {
      return ResponseEntity.ok().build();
    }
    return ResponseEntity.notFound().build();
  }

  @PostMapping("/{threadId}/replies")
  public ResponseEntity<?> addReply(
    @PathVariable Long courseId,
    @PathVariable Long threadId,
    @RequestBody DiscussionReply reply,
    @RequestHeader(value = "X-User-Id", required = false) Long userId,
    @RequestHeader(value = "X-User-Role", required = false) String userRole
  ) {
    if (userId == null) {
      return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", "Missing user id"));
    }
    if (!checkEnrollmentOrAccess(courseId, userId, userRole)) {
      return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(Map.of("error", "You must be enrolled to reply to discussions"));
    }

    reply.setUserId(userId);
    if (reply.getUserName() == null || reply.getUserName().isBlank()) {
      reply.setUserName("Student");
    }
    return ResponseEntity.ok(discussionService.addReply(threadId, reply));
  }

  @DeleteMapping("/{threadId}/replies/{replyId}")
  public ResponseEntity<?> deleteReply(
    @PathVariable Long courseId,
    @PathVariable Long threadId,
    @PathVariable Long replyId,
    @RequestHeader(value = "X-User-Id", required = false) Long userId,
    @RequestHeader(value = "X-User-Role", required = false) String userRole
  ) {
    if (userId == null && !"ADMIN".equalsIgnoreCase(userRole)) {
      return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", "Missing user id"));
    }
    if (discussionService.deleteReply(replyId, userId, userRole)) {
      return ResponseEntity.ok().build();
    }
    return ResponseEntity.notFound().build();
  }

  @PutMapping("/{threadId}/pin")
  public ResponseEntity<DiscussionThread> pinThread(
    @PathVariable Long courseId,
    @PathVariable Long threadId,
    @RequestParam(defaultValue = "true") boolean pinned,
    @RequestHeader(value = "X-User-Id", required = false) Long userId,
    @RequestHeader(value = "X-User-Role", required = false) String userRole
  ) {
    if (userId == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    return ResponseEntity.ok(
      discussionService.pinThread(threadId, userId, userRole, pinned)
    );
  }

  @PutMapping("/{threadId}/close")
  public ResponseEntity<DiscussionThread> closeThread(
    @PathVariable Long courseId,
    @PathVariable Long threadId,
    @RequestParam(defaultValue = "true") boolean closed,
    @RequestHeader(value = "X-User-Id", required = false) Long userId,
    @RequestHeader(value = "X-User-Role", required = false) String userRole
  ) {
    if (userId == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    return ResponseEntity.ok(
      discussionService.closeThread(threadId, userId, userRole, closed)
    );
  }

  @PutMapping("/{threadId}/replies/{replyId}/accept")
  public ResponseEntity<DiscussionReply> acceptReply(
    @PathVariable Long courseId,
    @PathVariable Long threadId,
    @PathVariable Long replyId,
    @RequestHeader(value = "X-User-Id", required = false) Long userId,
    @RequestHeader(value = "X-User-Role", required = false) String userRole
  ) {
    if (userId == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    return ResponseEntity.ok(
      discussionService.acceptReply(threadId, replyId, userId, userRole)
    );
  }

  @PutMapping("/{threadId}/replies/{replyId}/upvote")
  public ResponseEntity<?> upvoteReply(
    @PathVariable Long courseId,
    @PathVariable Long threadId,
    @PathVariable Long replyId,
    @RequestHeader(value = "X-User-Id", required = false) Long userId,
    @RequestHeader(value = "X-User-Role", required = false) String userRole
  ) {
    if (userId == null) {
      return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", "Missing user id"));
    }
    if (!checkEnrollmentOrAccess(courseId, userId, userRole)) {
      return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(Map.of("error", "You must be enrolled to vote on discussions"));
    }
    return ResponseEntity.ok(discussionService.upvoteReply(threadId, replyId));
  }
}
