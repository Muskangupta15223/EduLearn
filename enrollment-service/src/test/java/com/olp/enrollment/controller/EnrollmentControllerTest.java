package com.olp.enrollment.controller;

import com.olp.enrollment.dto.EnrollmentDto;
import com.olp.enrollment.dto.EnrollmentMapper;
import com.olp.enrollment.dto.CertificateResponse;
import com.olp.enrollment.dto.LessonStatusResponse;
import com.olp.enrollment.model.Enrollment;
import com.olp.enrollment.service.EnrollmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentControllerTest {

    @Mock
    private EnrollmentService enrollmentService;

    private EnrollmentController controller;

    @BeforeEach
    void setUp() {
        controller = new EnrollmentController(enrollmentService);
    }

    // --- create ---

    @Test
    void create_setsUserIdFromHeader() {
        Enrollment enrollment = new Enrollment();
        enrollment.setCourseId(100L);

        Enrollment saved = buildEnrollment(1L, 5L, 100L, "ACTIVE");
        when(enrollmentService.createEnrollment(any(Enrollment.class))).thenReturn(saved);

        ResponseEntity<EnrollmentDto> response = controller.create(enrollment, 5L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(5L, response.getBody().userId());
    }

    // --- getByStudent ---

    @Test
    void getByStudent_returnsList() {
        when(enrollmentService.getEnrollmentsByStudent(1L))
                .thenReturn(List.of(buildEnrollment(1L, 1L, 100L, "ACTIVE")));

        ResponseEntity<List<EnrollmentDto>> response = controller.getByStudent(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    // --- unenroll ---

    @Test
    void unenroll_success() {
        when(enrollmentService.unenroll(1L, 100L)).thenReturn(true);
        ResponseEntity<?> response = controller.unenroll(100L, 1L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void unenroll_notFound() {
        when(enrollmentService.unenroll(1L, 200L)).thenReturn(false);
        ResponseEntity<?> response = controller.unenroll(200L, 1L);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void unenroll_noUserId_throwsUnauthorized() {
        assertThrows(ResponseStatusException.class, () -> controller.unenroll(100L, null));
    }

    // --- getMyEnrollments ---

    @Test
    void getMyEnrollments_success() {
        when(enrollmentService.getMyEnrollments(1L))
                .thenReturn(List.of(buildEnrollment(1L, 1L, 100L, "ACTIVE")));

        ResponseEntity<List<EnrollmentDto>> response = controller.getMyEnrollments(1L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getMyEnrollments_noUserId_throwsUnauthorized() {
        assertThrows(ResponseStatusException.class, () -> controller.getMyEnrollments(null));
    }

    // --- getAll ---

    @Test
    void getAll_success() {
        when(enrollmentService.getAllEnrollments()).thenReturn(List.of());
        ResponseEntity<List<EnrollmentDto>> response = controller.getAll();
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // --- checkEnrollment ---

    @Test
    void checkEnrollment_enrolled_returnsTrue() {
        when(enrollmentService.isEnrolled(1L, 100L)).thenReturn(true);
        ResponseEntity<Map<String, Object>> response = controller.checkEnrollment(100L, 1L);
        assertEquals(true, response.getBody().get("enrolled"));
    }

    @Test
    void checkEnrollment_notEnrolled_returnsFalse() {
        when(enrollmentService.isEnrolled(1L, 200L)).thenReturn(false);
        ResponseEntity<Map<String, Object>> response = controller.checkEnrollment(200L, 1L);
        assertEquals(false, response.getBody().get("enrolled"));
    }

    // --- getByCourse ---

    @Test
    void getByCourse_returnsList() {
        when(enrollmentService.getEnrollmentsByCourse(100L)).thenReturn(List.of());
        ResponseEntity<List<EnrollmentDto>> response = controller.getByCourse(100L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // --- getStudentCount ---

    @Test
    void getStudentCount_returnsCount() {
        when(enrollmentService.getStudentCount(100L)).thenReturn(42L);
        ResponseEntity<Map<String, Long>> response = controller.getStudentCount(100L);
        assertEquals(42L, response.getBody().get("count"));
    }

    // --- markComplete ---

    @Test
    void markComplete_success() {
        when(enrollmentService.markComplete(1L, 100L))
                .thenReturn(Optional.of(buildEnrollment(1L, 1L, 100L, "COMPLETED")));

        ResponseEntity<EnrollmentDto> response = controller.markComplete(100L, 1L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("COMPLETED", response.getBody().status());
    }

    @Test
    void markComplete_notFound() {
        when(enrollmentService.markComplete(1L, 999L)).thenReturn(Optional.empty());
        ResponseEntity<EnrollmentDto> response = controller.markComplete(999L, 1L);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // --- updateStatus ---

    @Test
    void updateStatus_success() {
        when(enrollmentService.updateStatus(1L, "ACTIVE"))
                .thenReturn(Optional.of(buildEnrollment(1L, 1L, 100L, "ACTIVE")));

        ResponseEntity<EnrollmentDto> response = controller.updateStatus(1L, "ACTIVE");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // --- getProgressByCourses ---

    @Test
    void getProgressByCourses_emptyBody_returnsEmpty() {
        ResponseEntity<List<EnrollmentDto>> response =
                controller.getProgressByCourses(Map.of("courseIds", List.of()));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    // --- getAllCertificates ---

    @Test
    void getAllCertificates_adminRole_success() {
        when(enrollmentService.getAllCertificates()).thenReturn(List.of());
        ResponseEntity<List<CertificateResponse>> response = controller.getAllCertificates("ADMIN");
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getAllCertificates_nonAdmin_throwsForbidden() {
        assertThrows(ResponseStatusException.class, () -> controller.getAllCertificates("STUDENT"));
    }

    // --- Helper ---

    private Enrollment buildEnrollment(Long id, Long userId, Long courseId, String status) {
        Enrollment e = new Enrollment();
        e.setId(id);
        e.setUserId(userId);
        e.setCourseId(courseId);
        e.setStatus(status);
        e.setProgress(0);
        e.setEnrolledAt(LocalDateTime.now());
        e.setCertificateIssued(false);
        return e;
    }
}
