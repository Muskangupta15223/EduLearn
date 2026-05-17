package com.olp.course.controller;

import com.olp.course.dto.AssignmentDtos;
import com.olp.course.dto.AssignmentDtos.AssignmentRequest;
import com.olp.course.dto.AssignmentDtos.AssignmentResponse;
import com.olp.course.dto.AssignmentDtos.AssignmentSubmissionRequest;
import com.olp.course.dto.AssignmentDtos.AssignmentSubmissionResponse;
import com.olp.course.dto.AssignmentDtos.GradeSubmissionRequest;
import com.olp.course.service.AssignmentService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping("/courses/{courseId}/assignments")
    public ResponseEntity<AssignmentResponse> create(
            @PathVariable Long courseId,
            @RequestBody AssignmentRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return ResponseEntity.ok(
                AssignmentResponse.from(
                        assignmentService.createAssignment(courseId, request.toEntity(), userId, role)
                )
        );
    }

    @GetMapping("/courses/{courseId}/assignments")
    public ResponseEntity<List<AssignmentResponse>> getByCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(AssignmentDtos.toAssignmentResponses(assignmentService.getAssignmentsByCourse(courseId)));
    }

    @GetMapping("/assignments/{id}")
    public ResponseEntity<AssignmentResponse> getById(@PathVariable Long id) {
        return assignmentService.getAssignmentById(id)
                .map(AssignmentResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/assignments/{id}")
    public ResponseEntity<AssignmentResponse> update(
            @PathVariable Long id,
            @RequestBody AssignmentRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return assignmentService.updateAssignment(id, request.toEntity(), userId, role)
                .map(AssignmentResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/assignments/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        if (assignmentService.deleteAssignment(id, userId, role)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/assignments/{id}/submit")
    public ResponseEntity<AssignmentSubmissionResponse> submit(
            @PathVariable Long id,
            @RequestBody AssignmentSubmissionRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        String submissionText = request.submissionText() == null ? "" : request.submissionText();
        return ResponseEntity.ok(
                AssignmentSubmissionResponse.from(
                        assignmentService.submitAssignment(id, userId, submissionText, request.fileUrl())
                )
        );
    }

    @GetMapping("/assignments/{id}/submission/me")
    public ResponseEntity<AssignmentSubmissionResponse> getMySubmission(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return assignmentService.getMySubmission(id, userId)
                .map(AssignmentSubmissionResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/assignments/{id}/submissions")
    public ResponseEntity<List<AssignmentSubmissionResponse>> getSubmissions(@PathVariable Long id) {
        return ResponseEntity.ok(AssignmentDtos.toSubmissionResponses(assignmentService.getSubmissionsByAssignment(id)));
    }

    @PutMapping("/submissions/{subId}/grade")
    public ResponseEntity<AssignmentSubmissionResponse> gradeSubmission(
            @PathVariable Long subId,
            @RequestBody GradeSubmissionRequest request
    ) {
        return assignmentService.gradeSubmission(subId, request.score(), request.feedback())
                .map(AssignmentSubmissionResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
