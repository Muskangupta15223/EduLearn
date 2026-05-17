package com.olp.enrollment.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.olp.enrollment.dto.CertificateResponse;
import com.olp.enrollment.dto.EnrollmentCreateRequest;
import com.olp.enrollment.dto.EnrollmentDto;
import com.olp.enrollment.model.Enrollment;
import com.olp.enrollment.service.EnrollmentService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EnrollmentControllerTest {

  @Mock
  private EnrollmentService enrollmentService;

  private EnrollmentController controller;

  @BeforeEach
  void setUp() {
    controller = new EnrollmentController(enrollmentService);
  }

  @Test
  void create_setsUserIdFromHeader() {
    Enrollment saved = buildEnrollment(1L, 5L, 100L, "ACTIVE");
    when(enrollmentService.createEnrollment(any(Enrollment.class))).thenReturn(saved);

    ResponseEntity<EnrollmentDto> response = controller.create(new EnrollmentCreateRequest(null, 100L, null, null), 5L);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(5L, response.getBody().userId());
  }

  @Test
  void create_keepsRequestUserIdWhenPresent() {
    Enrollment saved = buildEnrollment(2L, 8L, 101L, "ACTIVE");
    when(enrollmentService.createEnrollment(any(Enrollment.class))).thenReturn(saved);

    ResponseEntity<EnrollmentDto> response = controller.create(new EnrollmentCreateRequest(8L, 101L, null, null), 5L);

    assertEquals(8L, response.getBody().userId());
  }

  @Test
  void getByStudent_returnsList() {
    when(enrollmentService.getEnrollmentsByStudent(1L)).thenReturn(List.of(buildEnrollment(1L, 1L, 100L, "ACTIVE")));

    ResponseEntity<List<EnrollmentDto>> response = controller.getByStudent(1L);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(1, response.getBody().size());
  }

  @Test
  void unenroll_success() {
    when(enrollmentService.unenroll(1L, 100L)).thenReturn(true);
    ResponseEntity<Void> response = controller.unenroll(100L, 1L);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  @Test
  void unenroll_notFound() {
    when(enrollmentService.unenroll(1L, 200L)).thenReturn(false);
    ResponseEntity<Void> response = controller.unenroll(200L, 1L);
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
  }

  @Test
  void unenroll_noUserId_throwsUnauthorized() {
    assertThrows(ResponseStatusException.class, () -> controller.unenroll(100L, null));
  }

  @Test
  void getMyEnrollments_success() {
    when(enrollmentService.getMyEnrollments(1L)).thenReturn(List.of(buildEnrollment(1L, 1L, 100L, "ACTIVE")));

    ResponseEntity<List<EnrollmentDto>> response = controller.getMyEnrollments(1L);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  @Test
  void getMyEnrollments_noUserId_throwsUnauthorized() {
    assertThrows(ResponseStatusException.class, () -> controller.getMyEnrollments(null));
  }

  @Test
  void getAll_success() {
    when(enrollmentService.getAllEnrollments()).thenReturn(List.of());
    ResponseEntity<List<EnrollmentDto>> response = controller.getAll();
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

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

  @Test
  void create_preservesStatusAndProgressFromRequest() {
    Enrollment saved = buildEnrollment(3L, 5L, 102L, "ACTIVE");
    saved.setProgress(40);
    when(enrollmentService.createEnrollment(any(Enrollment.class))).thenReturn(saved);

    ResponseEntity<EnrollmentDto> response = controller.create(new EnrollmentCreateRequest(5L, 102L, "ACTIVE", 40), 5L);

    assertEquals("ACTIVE", response.getBody().status());
    assertEquals(40, response.getBody().progress());
  }

  @Test
  void getByCourse_returnsList() {
    when(enrollmentService.getEnrollmentsByCourse(100L)).thenReturn(List.of());
    ResponseEntity<List<EnrollmentDto>> response = controller.getByCourse(100L);
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  @Test
  void getStudentCount_returnsCount() {
    when(enrollmentService.getStudentCount(100L)).thenReturn(42L);
    ResponseEntity<Map<String, Long>> response = controller.getStudentCount(100L);
    assertEquals(42L, response.getBody().get("count"));
  }

  @Test
  void markComplete_success() {
    when(enrollmentService.markComplete(1L, 100L)).thenReturn(Optional.of(buildEnrollment(1L, 1L, 100L, "COMPLETED")));

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

  @Test
  void updateStatus_success() {
    when(enrollmentService.updateStatus(1L, "ACTIVE")).thenReturn(Optional.of(buildEnrollment(1L, 1L, 100L, "ACTIVE")));

    ResponseEntity<EnrollmentDto> response = controller.updateStatus(1L, "ACTIVE");
    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  @Test
  void getProgressByCourses_emptyBody_returnsEmpty() {
    ResponseEntity<List<EnrollmentDto>> response = controller.getProgressByCourses(Map.of("courseIds", List.of()));
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertTrue(response.getBody().isEmpty());
  }

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
