package com.olp.course.controller;

import com.olp.course.model.Assignment;
import com.olp.course.model.AssignmentSubmission;
import com.olp.course.service.AssignmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    // ── Assignment CRUD ──

    @PostMapping("/courses/{courseId}/assignments")
    public ResponseEntity<Assignment> create(
            @PathVariable Long courseId,
            @RequestBody Assignment assignment,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return ResponseEntity.ok(assignmentService.createAssignment(courseId, assignment, userId, role));
    }

    @GetMapping("/courses/{courseId}/assignments")
    public ResponseEntity<List<Assignment>> getByCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(assignmentService.getAssignmentsByCourse(courseId));
    }

    @GetMapping("/assignments/{id}")
    public ResponseEntity<Assignment> getById(@PathVariable Long id) {
        return assignmentService.getAssignmentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/assignments/{id}")
    public ResponseEntity<Assignment> update(
            @PathVariable Long id,
            @RequestBody Assignment assignment,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return assignmentService.updateAssignment(id, assignment, userId, role)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/assignments/{id}")
    public ResponseEntity<?> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        if (assignmentService.deleteAssignment(id, userId, role)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ── Submissions ──

    @PostMapping("/assignments/{id}/submit")
    public ResponseEntity<AssignmentSubmission> submit(@PathVariable Long id,
                                                        @RequestBody Map<String, String> body,
                                                        @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        String submissionText = body.getOrDefault("submissionText", "");
        String fileUrl = body.getOrDefault("fileUrl", null);
        return ResponseEntity.ok(assignmentService.submitAssignment(id, userId, submissionText, fileUrl));
    }

    @GetMapping("/assignments/{id}/submission/me")
    public ResponseEntity<AssignmentSubmission> getMySubmission(@PathVariable Long id,
                                                                  @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return assignmentService.getMySubmission(id, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/assignments/{id}/submissions")
    public ResponseEntity<List<AssignmentSubmission>> getSubmissions(@PathVariable Long id) {
        return ResponseEntity.ok(assignmentService.getSubmissionsByAssignment(id));
    }

    @PutMapping("/submissions/{subId}/grade")
    public ResponseEntity<AssignmentSubmission> gradeSubmission(@PathVariable Long subId,
                                                                  @RequestBody Map<String, Object> body) {
        Integer score = body.get("score") != null ? Integer.valueOf(body.get("score").toString()) : null;
        String feedback = body.get("feedback") != null ? body.get("feedback").toString() : null;
        return assignmentService.gradeSubmission(subId, score, feedback)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
