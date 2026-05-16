package com.olp.enrollment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.enrollment.dto.CertificateResponse;
import com.olp.enrollment.dto.LessonStatusResponse;
import com.olp.enrollment.model.Certificate;
import com.olp.enrollment.model.Enrollment;
import com.olp.enrollment.model.LessonProgress;
import com.olp.enrollment.repository.CertificateRepository;
import com.olp.enrollment.repository.EnrollmentRepository;
import com.olp.enrollment.repository.LessonProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock private EnrollmentRepository repository;
    @Mock private LessonProgressRepository lessonProgressRepository;
    @Mock private CertificateRepository certificateRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private RestTemplate restTemplate;

    private EnrollmentService enrollmentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentService(
                repository, lessonProgressRepository, certificateRepository,
                kafkaTemplate, objectMapper, restTemplate
        );
    }

    // --- createEnrollment ---

    @Test
    void createEnrollment_newEnrollment_setsDefaults() {
        Enrollment enrollment = new Enrollment();
        enrollment.setUserId(1L);
        enrollment.setCourseId(100L);

        when(repository.findByUserIdAndCourseId(1L, 100L)).thenReturn(Optional.empty());
        when(restTemplate.getForObject(anyString(), eq(java.util.Map.class))).thenReturn(null);
        when(repository.save(any(Enrollment.class))).thenAnswer(i -> {
            Enrollment e = i.getArgument(0);
            e.setId(1L);
            return e;
        });

        Enrollment result = enrollmentService.createEnrollment(enrollment);

        assertNotNull(result.getEnrolledAt());
        assertEquals(0, result.getProgress());
    }

    @Test
    void createEnrollment_existingEnrollment_updatesStatus() {
        Enrollment existing = new Enrollment();
        existing.setId(1L);
        existing.setUserId(1L);
        existing.setCourseId(100L);
        existing.setStatus("PENDING_PAYMENT");
        existing.setProgress(0);
        existing.setEnrolledAt(LocalDateTime.now());

        Enrollment incoming = new Enrollment();
        incoming.setUserId(1L);
        incoming.setCourseId(100L);
        incoming.setStatus("ACTIVE");

        when(repository.findByUserIdAndCourseId(1L, 100L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));

        Enrollment result = enrollmentService.createEnrollment(incoming);

        assertEquals("ACTIVE", result.getStatus());
    }

    // --- getEnrollmentsByStudent ---

    @Test
    void getEnrollmentsByStudent_returnsList() {
        when(repository.findByUserId(1L)).thenReturn(List.of(new Enrollment()));
        assertEquals(1, enrollmentService.getEnrollmentsByStudent(1L).size());
    }

    // --- getAllEnrollments ---

    @Test
    void getAllEnrollments_returnsList() {
        when(repository.findAll()).thenReturn(List.of(new Enrollment(), new Enrollment()));
        assertEquals(2, enrollmentService.getAllEnrollments().size());
    }

    // --- isEnrolled ---

    @Test
    void isEnrolled_enrolled_returnsTrue() {
        when(repository.existsByUserIdAndCourseId(1L, 100L)).thenReturn(true);
        assertTrue(enrollmentService.isEnrolled(1L, 100L));
    }

    @Test
    void isEnrolled_notEnrolled_returnsFalse() {
        when(repository.existsByUserIdAndCourseId(1L, 200L)).thenReturn(false);
        assertFalse(enrollmentService.isEnrolled(1L, 200L));
    }

    // --- unenroll ---

    @Test
    void unenroll_existingEnrollment_returnsTrue() {
        Enrollment enrollment = new Enrollment();
        when(repository.findByUserIdAndCourseId(1L, 100L)).thenReturn(Optional.of(enrollment));
        assertTrue(enrollmentService.unenroll(1L, 100L));
        verify(repository).delete(enrollment);
    }

    @Test
    void unenroll_nonExistingEnrollment_returnsFalse() {
        when(repository.findByUserIdAndCourseId(1L, 200L)).thenReturn(Optional.empty());
        assertFalse(enrollmentService.unenroll(1L, 200L));
    }

    // --- getStudentCount ---

    @Test
    void getStudentCount_returnsCount() {
        when(repository.countByCourseId(100L)).thenReturn(25L);
        assertEquals(25L, enrollmentService.getStudentCount(100L));
    }

    // --- updateProgress ---

    @Test
    void updateProgress_updatesEnrollment() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(1L);
        enrollment.setUserId(1L);
        enrollment.setCourseId(100L);
        enrollment.setProgress(0);

        when(repository.findByUserIdAndCourseId(1L, 100L)).thenReturn(Optional.of(enrollment));
        when(restTemplate.getForObject(anyString(), eq(java.util.Map.class))).thenReturn(null);
        when(repository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));

        Enrollment result = enrollmentService.updateProgress(1L, 100L, 50, null);

        assertEquals(50, result.getProgress());
    }

    @Test
    void updateProgress_completes_at100() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(1L);
        enrollment.setUserId(1L);
        enrollment.setCourseId(100L);
        enrollment.setProgress(90);

        when(repository.findByUserIdAndCourseId(1L, 100L)).thenReturn(Optional.of(enrollment));
        when(restTemplate.getForObject(anyString(), eq(java.util.Map.class))).thenReturn(null);
        when(certificateRepository.findByUserIdAndCourseId(1L, 100L)).thenReturn(Optional.of(new Certificate()));
        when(repository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));

        Enrollment result = enrollmentService.updateProgress(1L, 100L, 100, null);

        assertEquals("COMPLETED", result.getStatus());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    void updateProgress_enrollmentNotFound_throws() {
        when(repository.findByUserIdAndCourseId(1L, 999L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () ->
                enrollmentService.updateProgress(1L, 999L, 50, null));
    }

    // --- markComplete ---

    @Test
    void markComplete_setsCompleted() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(1L);
        enrollment.setUserId(1L);
        enrollment.setCourseId(100L);

        when(repository.findByUserIdAndCourseId(1L, 100L)).thenReturn(Optional.of(enrollment));
        when(certificateRepository.findByUserIdAndCourseId(1L, 100L)).thenReturn(Optional.of(new Certificate()));
        when(repository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));

        Optional<Enrollment> result = enrollmentService.markComplete(1L, 100L);

        assertTrue(result.isPresent());
        assertEquals(100, result.get().getProgress());
        assertEquals("COMPLETED", result.get().getStatus());
    }

    @Test
    void markComplete_nonExistent_returnsEmpty() {
        when(repository.findByUserIdAndCourseId(1L, 999L)).thenReturn(Optional.empty());
        assertTrue(enrollmentService.markComplete(1L, 999L).isEmpty());
    }

    // --- updateStatus ---

    @Test
    void updateStatus_setsNewStatus() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(1L);
        enrollment.setStatus("PENDING_PAYMENT");

        when(repository.findById(1L)).thenReturn(Optional.of(enrollment));
        when(repository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));

        Optional<Enrollment> result = enrollmentService.updateStatus(1L, "ACTIVE");

        assertTrue(result.isPresent());
        assertEquals("ACTIVE", result.get().getStatus());
    }

    @Test
    void updateStatus_completed_setsCompletedAt() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(1L);
        enrollment.setStatus("ACTIVE");

        when(repository.findById(1L)).thenReturn(Optional.of(enrollment));
        when(repository.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));

        Optional<Enrollment> result = enrollmentService.updateStatus(1L, "COMPLETED");

        assertTrue(result.isPresent());
        assertNotNull(result.get().getCompletedAt());
    }

    // --- getLessonStatus ---

    @Test
    void getLessonStatus_existing_returnsResponse() {
        LessonProgress lp = new LessonProgress();
        lp.setLessonId(10L);
        lp.setLessonTitle("Lesson 1");
        lp.setPercentComplete(50);
        lp.setStatus("IN_PROGRESS");

        when(lessonProgressRepository.findByUserIdAndCourseIdAndLessonId(1L, 100L, 10L))
                .thenReturn(Optional.of(lp));

        Optional<LessonStatusResponse> result = enrollmentService.getLessonStatus(1L, 100L, 10L);

        assertTrue(result.isPresent());
        assertEquals(10L, result.get().getLessonId());
        assertEquals("IN_PROGRESS", result.get().getStatus());
    }

    // --- getLessonStatuses ---

    @Test
    void getLessonStatuses_returnsList() {
        LessonProgress lp = new LessonProgress();
        lp.setLessonId(10L);
        lp.setStatus("COMPLETED");
        lp.setPercentComplete(100);

        when(lessonProgressRepository.findByUserIdAndCourseIdOrderByLessonIdAsc(1L, 100L))
                .thenReturn(List.of(lp));

        List<LessonStatusResponse> result = enrollmentService.getLessonStatuses(1L, 100L);
        assertEquals(1, result.size());
    }

    // --- getCertificate ---

    @Test
    void getCertificate_notEnrolled_returnsEmpty() {
        when(repository.findByUserIdAndCourseId(1L, 100L)).thenReturn(Optional.empty());
        assertTrue(enrollmentService.getCertificate(1L, 100L).isEmpty());
    }

    // --- getAllCertificates ---

    @Test
    void getAllCertificates_returnsList() {
        Certificate cert = new Certificate();
        cert.setId(1L);
        cert.setUserId(1L);
        cert.setCourseId(100L);
        cert.setCourseTitle("Java 101");
        cert.setCertificateNo("CERT-1");
        cert.setVerificationCode("ABC123");
        cert.setIssuedAt(LocalDateTime.now());

        when(certificateRepository.findAllByOrderByIssuedAtDesc()).thenReturn(List.of(cert));

        List<CertificateResponse> result = enrollmentService.getAllCertificates();
        assertEquals(1, result.size());
        assertEquals("Java 101", result.get(0).getCourseTitle());
    }

    // --- verifyCertificate ---

    @Test
    void verifyCertificate_valid_returnsResponse() {
        Certificate cert = new Certificate();
        cert.setId(1L);
        cert.setUserId(1L);
        cert.setCourseId(100L);
        cert.setCourseTitle("Java 101");
        cert.setCertificateNo("CERT-1");
        cert.setVerificationCode("ABC123");
        cert.setIssuedAt(LocalDateTime.now());

        when(certificateRepository.findByVerificationCode("ABC123")).thenReturn(Optional.of(cert));

        var result = enrollmentService.verifyCertificate("ABC123");
        assertTrue(result.isPresent());
        assertTrue(result.get().getValid());
    }

    @Test
    void verifyCertificate_invalid_returnsEmpty() {
        when(certificateRepository.findByVerificationCode("INVALID")).thenReturn(Optional.empty());
        assertTrue(enrollmentService.verifyCertificate("INVALID").isEmpty());
    }

    // --- getEnrollmentsByCourse ---

    @Test
    void getEnrollmentsByCourse_returnsList() {
        when(repository.findByCourseId(100L)).thenReturn(List.of(new Enrollment()));
        assertEquals(1, enrollmentService.getEnrollmentsByCourse(100L).size());
    }

    // --- getEnrollmentsByCourseIds ---

    @Test
    void getEnrollmentsByCourseIds_returnsList() {
        when(repository.findByCourseIdIn(List.of(100L, 200L))).thenReturn(List.of(new Enrollment(), new Enrollment()));
        assertEquals(2, enrollmentService.getEnrollmentsByCourseIds(List.of(100L, 200L)).size());
    }

    // --- checkEnrollment ---

    @Test
    void checkEnrollment_existing_returnsPresent() {
        when(repository.findByUserIdAndCourseId(1L, 100L)).thenReturn(Optional.of(new Enrollment()));
        assertTrue(enrollmentService.checkEnrollment(1L, 100L).isPresent());
    }
}
