package com.olp.course.controller;

import com.olp.course.model.CommentReport;
import com.olp.course.model.CourseReport;
import com.olp.course.model.ModerationAction;
import com.olp.course.model.ReportStatus;
import com.olp.course.service.ModerationService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping
public class ModerationController {
    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @PostMapping("/courses/{courseId}/reports")
    public ResponseEntity<CourseReport> reportCourse(
            @PathVariable Long courseId,
            @RequestBody CourseReport report,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUser(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(moderationService.reportCourse(courseId, report, userId));
    }

    @PostMapping("/courses/{courseId}/comment-reports")
    public ResponseEntity<CommentReport> reportComment(
            @PathVariable Long courseId,
            @RequestBody CommentReport report,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUser(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(moderationService.reportComment(courseId, report, userId));
    }

    @GetMapping("/moderation/course-reports")
    public ResponseEntity<?> getCourseReports(
            @RequestParam(value = "status", required = false) ReportStatus status,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.ok(moderationService.getCourseReports(status, role));
    }

    @GetMapping("/moderation/comment-reports")
    public ResponseEntity<?> getCommentReports(
            @RequestParam(value = "status", required = false) ReportStatus status,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.ok(moderationService.getCommentReports(status, role));
    }

    @PutMapping("/moderation/course-reports/{reportId}")
    public ResponseEntity<CourseReport> reviewCourseReport(
            @PathVariable Long reportId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.ok(moderationService.reviewCourseReport(
                reportId,
                parseStatus(body.get("status")),
                body.get("comment"),
                userId,
                role));
    }

    @PutMapping("/moderation/comment-reports/{reportId}")
    public ResponseEntity<CommentReport> reviewCommentReport(
            @PathVariable Long reportId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.ok(moderationService.reviewCommentReport(
                reportId,
                parseStatus(body.get("status")),
                body.get("comment"),
                userId,
                role));
    }

    @PostMapping("/moderation/actions")
    public ResponseEntity<ModerationAction> createAction(
            @RequestBody ModerationAction action,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.status(HttpStatus.CREATED).body(moderationService.createAction(action, userId, role));
    }

    private ReportStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return ReportStatus.IN_REVIEW;
        }
        return ReportStatus.valueOf(status.trim().toUpperCase());
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user id");
        }
    }
}
