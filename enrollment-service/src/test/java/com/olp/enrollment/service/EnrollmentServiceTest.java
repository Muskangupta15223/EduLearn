package com.olp.enrollment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.olp.enrollment.dto.CertificateVerificationResponse;
import com.olp.enrollment.model.Certificate;
import com.olp.enrollment.model.Enrollment;
import com.olp.enrollment.repository.CertificateRepository;
import com.olp.enrollment.repository.EnrollmentRepository;
import com.olp.enrollment.repository.LessonProgressRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private LessonProgressRepository lessonProgressRepository;

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private RestTemplate restTemplate;

    private EnrollmentService enrollmentService;

    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentService(
                enrollmentRepository,
                lessonProgressRepository,
                certificateRepository,
                kafkaTemplate,
                new ObjectMapper(),
                restTemplate
        );
    }

    @Test
    void verifyCertificateReturnsValidMetadataWhenCodeExists() {
        Certificate certificate = new Certificate();
        certificate.setId(9L);
        certificate.setUserId(4L);
        certificate.setCourseId(12L);
        certificate.setCourseTitle("Spring Boot");
        certificate.setInstructorName("Asha");
        certificate.setCertificateNo("CERT-12-4");
        certificate.setVerificationCode("VERIFY123");
        certificate.setIssuedAt(LocalDateTime.now());

        when(certificateRepository.findByVerificationCode("VERIFY123")).thenReturn(Optional.of(certificate));

        CertificateVerificationResponse response = enrollmentService.verifyCertificate("VERIFY123").orElseThrow();

        assertTrue(response.getValid());
        assertEquals("VERIFY123", response.getVerificationCode());
        assertEquals("Spring Boot", response.getCourseTitle());
    }

    @Test
    void createEnrollmentReusesExistingRecordAndActivatesFreeCourse() throws Exception {
        Enrollment existing = new Enrollment();
        existing.setId(5L);
        existing.setUserId(4L);
        existing.setCourseId(12L);
        existing.setProgress(null);
        existing.setStatus(null);

        Enrollment request = new Enrollment();
        request.setUserId(4L);
        request.setCourseId(12L);

        when(restTemplate.getForObject("http://course-service/courses/12", Map.class))
                .thenReturn(Map.of("price", 0, "title", "Spring Boot", "instructorId", 9L));
        when(enrollmentRepository.findByUserIdAndCourseId(4L, 12L)).thenReturn(Optional.of(existing));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Enrollment result = enrollmentService.createEnrollment(request);

        assertEquals("ACTIVE", result.getStatus());
        assertEquals(0, result.getProgress());
        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("course-events"), eventCaptor.capture());
        JsonNode event = new ObjectMapper().readTree(eventCaptor.getValue());
        assertEquals("STUDENT_ENROLLED", event.get("eventType").asText());
        assertEquals("Spring Boot", event.get("courseTitle").asText());
        assertEquals(9L, event.get("instructorId").asLong());
    }
}
