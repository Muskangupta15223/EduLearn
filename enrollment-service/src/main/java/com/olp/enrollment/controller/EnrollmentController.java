package com.olp.enrollment.controller;

import com.olp.enrollment.constant.EnrollmentConstants;
import com.olp.enrollment.dto.EnrollmentDto;
import com.olp.enrollment.dto.EnrollmentMapper;
import com.olp.enrollment.model.Enrollment;
import com.olp.enrollment.service.EnrollmentService;
import com.olp.enrollment.dto.CertificateResponse;
import com.olp.enrollment.dto.CertificateVerificationResponse;
import com.olp.enrollment.dto.LessonStatusResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping
    public ResponseEntity<EnrollmentDto> create(
            @RequestBody Enrollment enrollment,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (enrollment.getUserId() == null && userId != null) {
            enrollment.setUserId(userId);
        }
        return ResponseEntity.ok(EnrollmentMapper.toDto(enrollmentService.createEnrollment(enrollment)));
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<EnrollmentDto>> getByStudent(@PathVariable Long studentId) {
        return ResponseEntity.ok(EnrollmentMapper.toDtoList(enrollmentService.getEnrollmentsByStudent(studentId)));
    }

    @DeleteMapping("/{courseId}")
    public ResponseEntity<?> unenroll(
            @PathVariable Long courseId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUserId(userId);
        if (enrollmentService.unenroll(userId, courseId)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/me")
    public ResponseEntity<List<EnrollmentDto>> getMyEnrollments(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUserId(userId);
        return ResponseEntity.ok(EnrollmentMapper.toDtoList(enrollmentService.getMyEnrollments(userId)));
    }

    @GetMapping("/all")
    public ResponseEntity<List<EnrollmentDto>> getAll() {
        return ResponseEntity.ok(EnrollmentMapper.toDtoList(enrollmentService.getAllEnrollments()));
    }

    // Check if a user is enrolled in a course
    @GetMapping("/check/{courseId}")
    public ResponseEntity<Map<String, Object>> checkEnrollment(
            @PathVariable Long courseId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUserId(userId);
        boolean enrolled = enrollmentService.isEnrolled(userId, courseId);
        return ResponseEntity.ok(Map.of("enrolled", enrolled));
    }

    // Get enrollments for a specific course (instructor)
    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<EnrollmentDto>> getByCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(EnrollmentMapper.toDtoList(enrollmentService.getEnrollmentsByCourse(courseId)));
    }

    // Get student count for a course
    @GetMapping("/course/{courseId}/count")
    public ResponseEntity<Map<String, Long>> getStudentCount(@PathVariable Long courseId) {
        long count = enrollmentService.getStudentCount(courseId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    // Get enrollments for multiple courses (instructor progress tracking)
    @PostMapping("/courses/progress")
    public ResponseEntity<List<EnrollmentDto>> getProgressByCourses(@RequestBody Map<String, List<Long>> body) {
        List<Long> courseIds = body.get("courseIds");
        if (courseIds == null || courseIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(EnrollmentMapper.toDtoList(enrollmentService.getEnrollmentsByCourseIds(courseIds)));
    }

    // Update progress
    @PutMapping("/{courseId}/progress")
    public ResponseEntity<EnrollmentDto> updateProgress(
            @PathVariable Long courseId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUserId(userId);
        Integer percent = Integer.valueOf(body.get("percent").toString());
        String lastLesson = body.containsKey("lessonId") ? body.get("lessonId").toString() : null;
        return ResponseEntity.ok(EnrollmentMapper.toDto(enrollmentService.updateProgress(userId, courseId, percent, lastLesson)));
    }

    // Mark course as complete
    @PutMapping("/{courseId}/complete")
    public ResponseEntity<EnrollmentDto> markComplete(
            @PathVariable Long courseId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUserId(userId);
        return enrollmentService.markComplete(userId, courseId)
                .map(EnrollmentMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Legacy progress update endpoint (keep backward compatibility)
    @PutMapping("/progress")
    public ResponseEntity<EnrollmentDto> updateProgressLegacy(@RequestBody Map<String, Object> body) {
        Long studentId = Long.valueOf(body.get("studentId").toString());
        Long courseId = Long.valueOf(body.get("courseId").toString());
        Integer percent = Integer.valueOf(body.get("percent").toString());
        String lastLesson = body.containsKey("lastLesson") ? body.get("lastLesson").toString() : null;
        return ResponseEntity.ok(EnrollmentMapper.toDto(enrollmentService.updateProgress(studentId, courseId, percent, lastLesson)));
    }

    @GetMapping("/{courseId}/lessons/{lessonId}/status")
    public ResponseEntity<LessonStatusResponse> getLessonStatus(
            @PathVariable Long courseId,
            @PathVariable Long lessonId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUserId(userId);
        return enrollmentService.getLessonStatus(userId, courseId, lessonId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{courseId}/lessons")
    public ResponseEntity<List<LessonStatusResponse>> getLessonStatuses(
            @PathVariable Long courseId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUserId(userId);
        return ResponseEntity.ok(enrollmentService.getLessonStatuses(userId, courseId));
    }

    @GetMapping("/{courseId}/certificate")
    public ResponseEntity<byte[]> downloadCertificate(
            @PathVariable Long courseId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        requireUserId(userId);
        return enrollmentService.generateCertificateImage(userId, courseId, userName)
                .map(imageBytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"certificate-course-" + courseId + ".png\"")
                        .contentType(MediaType.IMAGE_PNG)
                        .body(imageBytes))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{courseId}/certificate/metadata")
    public ResponseEntity<CertificateResponse> getCertificateMetadata(
            @PathVariable Long courseId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUserId(userId);
        return enrollmentService.getCertificate(userId, courseId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<EnrollmentDto> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return enrollmentService.updateStatus(id, status)
                .map(EnrollmentMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/certificates")
    public ResponseEntity<List<CertificateResponse>> getAllCertificates(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return ResponseEntity.ok(enrollmentService.getAllCertificates());
    }

    @GetMapping("/certificates/verify/{verificationCode}")
    public ResponseEntity<CertificateVerificationResponse> verifyCertificate(@PathVariable String verificationCode) {
        return enrollmentService.verifyCertificate(verificationCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, EnrollmentConstants.MSG_MISSING_USER_ID);
        }
    }

    private void requireAdmin(String role) {
        if (role == null || !EnrollmentConstants.ROLE_ADMIN.equalsIgnoreCase(role)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, EnrollmentConstants.MSG_ADMIN_ACCESS_REQUIRED);
        }
    }
}
