package com.olp.enrollment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.enrollment.dto.CertificateResponse;
import com.olp.enrollment.dto.CertificateVerificationResponse;
import com.olp.enrollment.dto.LessonStatusResponse;
import com.olp.enrollment.model.Certificate;
import com.olp.enrollment.model.Enrollment;
import com.olp.enrollment.model.LessonProgress;
import com.olp.enrollment.repository.CertificateRepository;
import com.olp.enrollment.repository.EnrollmentRepository;
import com.olp.enrollment.repository.LessonProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);

    private final EnrollmentRepository repository;
    private final LessonProgressRepository lessonProgressRepository;
    private final CertificateRepository certificateRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public EnrollmentService(
            EnrollmentRepository repository,
            LessonProgressRepository lessonProgressRepository,
            CertificateRepository certificateRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            RestTemplate restTemplate
    ) {
        this.repository = repository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.certificateRepository = certificateRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public Enrollment createEnrollment(Enrollment enrollment) {
        boolean isFree = isCourseFree(enrollment.getCourseId());
        Optional<Enrollment> existing = repository.findByUserIdAndCourseId(enrollment.getUserId(), enrollment.getCourseId());
        if (existing.isPresent()) {
            Enrollment current = existing.get();
            if (enrollment.getStatus() != null) {
                current.setStatus(enrollment.getStatus());
            } else if (isFree) {
                // Free course → always activate
                current.setStatus("ACTIVE");
            } else if (current.getStatus() == null) {
                current.setStatus("PENDING_PAYMENT");
            }
            if (enrollment.getProgress() != null) {
                current.setProgress(enrollment.getProgress());
            } else if (current.getProgress() == null) {
                current.setProgress(0);
            }
            if (current.getEnrolledAt() == null) {
                current.setEnrolledAt(LocalDateTime.now());
            }
            Enrollment savedExisting = repository.save(current);
            if ("ACTIVE".equals(savedExisting.getStatus())) {
                publishEnrollmentEvent(savedExisting);
            }
            return savedExisting;
        }

        enrollment.setEnrolledAt(LocalDateTime.now());
        if (enrollment.getStatus() == null) {
            enrollment.setStatus(isFree ? "ACTIVE" : "PENDING_PAYMENT");
        }
        if (enrollment.getProgress() == null) {
            enrollment.setProgress(0);
        }
        Enrollment saved = repository.save(enrollment);
        if ("ACTIVE".equals(saved.getStatus())) {
            publishEnrollmentEvent(saved);
        }
        return saved;
    }

    public List<Enrollment> getEnrollmentsByStudent(Long studentId) {
        return repository.findByUserId(studentId);
    }

    public List<Enrollment> getMyEnrollments(Long userId) {
        return repository.findByUserId(userId);
    }

    public List<Enrollment> getAllEnrollments() {
        return repository.findAll();
    }

    public List<Enrollment> getEnrollmentsByCourse(Long courseId) {
        return repository.findByCourseId(courseId);
    }

    public List<Enrollment> getEnrollmentsByCourseIds(List<Long> courseIds) {
        return repository.findByCourseIdIn(courseIds);
    }

    public long getStudentCount(Long courseId) {
        return repository.countByCourseId(courseId);
    }

    public boolean isEnrolled(Long userId, Long courseId) {
        return repository.existsByUserIdAndCourseId(userId, courseId);
    }

    public Optional<Enrollment> checkEnrollment(Long userId, Long courseId) {
        return repository.findByUserIdAndCourseId(userId, courseId);
    }

    public boolean unenroll(Long userId, Long courseId) {
        return repository.findByUserIdAndCourseId(userId, courseId).map(enrollment -> {
            repository.delete(enrollment);
            return true;
        }).orElse(false);
    }

    public Enrollment updateProgress(Long userId, Long courseId, Integer percent, String lastLesson) {
        Enrollment enrollment = repository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found"));

        Integer resolvedPercent = percent == null ? 0 : Math.max(0, Math.min(100, percent));
        if (lastLesson != null && !lastLesson.isBlank()) {
            Long lessonId = Long.valueOf(lastLesson);
            LessonProgress lessonProgress = lessonProgressRepository
                    .findByUserIdAndCourseIdAndLessonId(userId, courseId, lessonId)
                    .orElseGet(LessonProgress::new);
            lessonProgress.setUserId(userId);
            lessonProgress.setCourseId(courseId);
            lessonProgress.setLessonId(lessonId);
            lessonProgress.setLessonTitle(resolveLessonTitle(courseId, lessonId));
            lessonProgress.setPercentComplete(resolvedPercent);
            lessonProgress.setLastAccessedAt(LocalDateTime.now());

            if (resolvedPercent >= 100) {
                lessonProgress.setStatus("COMPLETED");
                lessonProgress.setCompletedAt(LocalDateTime.now());
            } else if (resolvedPercent > 0) {
                lessonProgress.setStatus("IN_PROGRESS");
            } else {
                lessonProgress.setStatus("STARTED");
            }

            lessonProgressRepository.save(lessonProgress);
            enrollment.setLastLesson(lessonProgress.getLessonTitle() != null ? lessonProgress.getLessonTitle() : lastLesson);
        }

        enrollment.setProgress(calculateCourseProgress(userId, courseId, resolvedPercent));
        if (enrollment.getProgress() >= 100) {
            enrollment.setStatus("COMPLETED");
            enrollment.setCompletedAt(LocalDateTime.now());
            issueCertificate(enrollment);
        }
        return repository.save(enrollment);
    }

    public Optional<Enrollment> updateStatus(Long id, String status) {
        return repository.findById(id).map(enrollment -> {
            enrollment.setStatus(status);
            if ("COMPLETED".equalsIgnoreCase(status) && enrollment.getCompletedAt() == null) {
                enrollment.setCompletedAt(LocalDateTime.now());
            }
            Enrollment saved = repository.save(enrollment);
            if ("ACTIVE".equals(status)) {
                publishEnrollmentEvent(saved);
            }
            return saved;
        });
    }

    public Optional<Enrollment> markComplete(Long userId, Long courseId) {
        return repository.findByUserIdAndCourseId(userId, courseId).map(enrollment -> {
            enrollment.setProgress(100);
            enrollment.setStatus("COMPLETED");
            enrollment.setCompletedAt(LocalDateTime.now());
            Enrollment saved = repository.save(enrollment);
            issueCertificate(saved);
            return saved;
        });
    }

    public Optional<LessonStatusResponse> getLessonStatus(Long userId, Long courseId, Long lessonId) {
        return lessonProgressRepository.findByUserIdAndCourseIdAndLessonId(userId, courseId, lessonId)
                .map(this::mapLessonProgress);
    }

    public List<LessonStatusResponse> getLessonStatuses(Long userId, Long courseId) {
        return lessonProgressRepository.findByUserIdAndCourseIdOrderByLessonIdAsc(userId, courseId)
                .stream()
                .map(this::mapLessonProgress)
                .collect(Collectors.toList());
    }

    public Optional<CertificateResponse> getCertificate(Long userId, Long courseId) {
        Optional<Enrollment> enrollmentOpt = repository.findByUserIdAndCourseId(userId, courseId);
        if (enrollmentOpt.isEmpty()) {
            return Optional.empty();
        }
        Enrollment enrollment = enrollmentOpt.get();
        if ((enrollment.getProgress() != null && enrollment.getProgress() >= 100) || "COMPLETED".equals(enrollment.getStatus())) {
            issueCertificate(enrollment);
        }
        return certificateRepository.findByUserIdAndCourseId(userId, courseId).map(this::mapCertificate);
    }

    public Optional<byte[]> generateCertificateImage(Long userId, Long courseId, String userName) {
        return getCertificate(userId, courseId).map(cert -> {
            try {
                int width = 1000;
                int height = 700;
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();

                // Background
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, width, height);

                // Decorative Border
                g.setColor(new Color(31, 111, 235)); // Brand primary blue
                g.setStroke(new BasicStroke(24));
                g.drawRect(12, 12, width - 24, height - 24);
                
                g.setStroke(new BasicStroke(2));
                g.drawRect(30, 30, width - 60, height - 60);

                // Anti-aliasing for smooth text
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // Content
                g.setColor(new Color(15, 23, 42)); // Dark slate text
                
                // Header
                g.setFont(new Font("Serif", Font.BOLD, 52));
                drawCenteredString(g, "EDULEARN LMS", width, 130);

                g.setFont(new Font("Serif", Font.PLAIN, 26));
                drawCenteredString(g, "CERTIFICATE OF COMPLETION", width, 180);

                // Body text
                g.setColor(new Color(71, 85, 105));
                g.setFont(new Font("Serif", Font.ITALIC, 22));
                drawCenteredString(g, "This is to certify that", width, 260);

                g.setColor(new Color(15, 23, 42));
                g.setFont(new Font("Serif", Font.BOLD, 42));
                String displayName = (userName != null && !userName.isBlank()) ? userName : "Learner #" + cert.getUserId();
                drawCenteredString(g, displayName, width, 320);

                g.setColor(new Color(71, 85, 105));
                g.setFont(new Font("Serif", Font.ITALIC, 22));
                drawCenteredString(g, "has successfully completed the course", width, 380);

                g.setColor(new Color(31, 111, 235));
                g.setFont(new Font("Serif", Font.BOLD, 36));
                drawCenteredString(g, cert.getCourseTitle(), width, 440);

                // Signatures / Details
                g.setColor(new Color(15, 23, 42));
                g.setFont(new Font("Serif", Font.PLAIN, 20));
                String instructor = cert.getInstructorName() != null ? cert.getInstructorName() : "EduLearn Instructor";
                drawCenteredString(g, "Instructor: " + instructor, width, 510);

                String issuedDate = cert.getIssuedAt() != null
                        ? cert.getIssuedAt().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))
                        : "N/A";
                drawCenteredString(g, "Issued on: " + issuedDate, width, 550);

                // Footer / Verification
                g.setColor(new Color(148, 163, 184));
                g.setFont(new Font("Monospaced", Font.PLAIN, 12));
                g.drawString("Certificate ID: " + cert.getCertificateNo(), 60, height - 70);
                g.drawString("Verification Code: " + cert.getVerificationCode(), 60, height - 50);
                
                // Add a small "Verified" mark
                g.setColor(new Color(16, 185, 129));
                g.setFont(new Font("SansSerif", Font.BOLD, 14));
                g.drawString("VERIFIED ACHIEVEMENT", width - 240, height - 60);

                g.dispose();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                return baos.toByteArray();
            } catch (Exception e) {
                log.error("Error generating certificate image: {}", e.getMessage(), e);
                return null;
            }
        });
    }

    private void drawCenteredString(Graphics2D g, String text, int width, int y) {
        FontMetrics fm = g.getFontMetrics();
        int x = (width - fm.stringWidth(text)) / 2;
        g.drawString(text, x, y);
    }

    public Optional<String> downloadCertificateContent(Long userId, Long courseId) {
        return getCertificate(userId, courseId).map(cert -> {
            String issuedDate = cert.getIssuedAt() != null
                    ? cert.getIssuedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                    : "N/A";
            return """
                    =====================================
                          EDULEARN LMS CERTIFICATE
                    =====================================
                    Certificate No: %s
                    Verification Code: %s

                    This is to certify that learner %s
                    has successfully completed the course:

                    %s

                    Instructor: %s
                    Issued On: %s

                    Congratulations on your achievement.
                    =====================================
                    """.formatted(
                    cert.getCertificateNo(),
                    cert.getVerificationCode(),
                    "User #" + cert.getUserId(),
                    cert.getCourseTitle(),
                    cert.getInstructorName() != null ? cert.getInstructorName() : "EduLearn Instructor",
                    issuedDate
            );
        });
    }

    public List<CertificateResponse> getAllCertificates() {
        return certificateRepository.findAllByOrderByIssuedAtDesc()
                .stream()
                .map(this::mapCertificate)
                .collect(Collectors.toList());
    }

    public Optional<CertificateVerificationResponse> verifyCertificate(String verificationCode) {
        return certificateRepository.findByVerificationCode(verificationCode)
                .map(certificate -> {
                    CertificateVerificationResponse response = new CertificateVerificationResponse();
                    response.setValid(true);
                    response.setCertificateId(certificate.getId());
                    response.setUserId(certificate.getUserId());
                    response.setCourseId(certificate.getCourseId());
                    response.setCourseTitle(certificate.getCourseTitle());
                    response.setInstructorName(certificate.getInstructorName());
                    response.setCertificateNo(certificate.getCertificateNo());
                    response.setVerificationCode(certificate.getVerificationCode());
                    response.setIssuedAt(certificate.getIssuedAt());
                    return response;
                });
    }

    private void publishEnrollmentEvent(Enrollment enrollment) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("enrollmentId", enrollment.getId());
            event.put("courseId", enrollment.getCourseId());
            event.put("userId", enrollment.getUserId());
            event.put("status", enrollment.getStatus());
            event.put("eventType", "STUDENT_ENROLLED");
            populateCourseEventDetails(event, enrollment.getCourseId());

            kafkaTemplate.send("course-events", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Error publishing enrollment event: {}", e.getMessage(), e);
        }
    }

    private void populateCourseEventDetails(Map<String, Object> event, Long courseId) {
        try {
            Map<?, ?> course = restTemplate.getForObject(
                    "http://course-service/courses/" + courseId, Map.class);
            if (course == null) {
                return;
            }
            Object title = course.get("title");
            Object instructorId = course.get("instructorId");
            if (title != null) {
                event.put("courseTitle", title.toString());
            }
            if (instructorId != null) {
                event.put("instructorId", Long.valueOf(instructorId.toString()));
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Check if a course is free (price is 0 or null).
     * Calls course-service via Eureka discovery.
     */
    private boolean isCourseFree(Long courseId) {
        try {
            Map<?, ?> course = restTemplate.getForObject(
                    "http://course-service/courses/" + courseId, Map.class);
            if (course != null) {
                Object price = course.get("price");
                if (price == null) return true;
                double priceValue = Double.parseDouble(price.toString());
                return priceValue <= 0;
            }
        } catch (Exception e) {
            log.warn("Could not determine course price for courseId={}: {}", courseId, e.getMessage());
        }
        return false;
    }

    private int calculateCourseProgress(Long userId, Long courseId, Integer fallbackPercent) {
        int totalLessons = resolveTotalLessons(courseId);
        if (totalLessons <= 0) {
            return fallbackPercent == null ? 0 : Math.max(0, Math.min(100, fallbackPercent));
        }
        long completedLessons = lessonProgressRepository.countByUserIdAndCourseIdAndStatus(userId, courseId, "COMPLETED");
        return (int) Math.min(100, Math.round((completedLessons * 100.0) / totalLessons));
    }

    private int resolveTotalLessons(Long courseId) {
        try {
            Map<?, ?> course = restTemplate.getForObject("http://course-service/courses/" + courseId, Map.class);
            if (course == null) {
                return 0;
            }
            Object modulesObj = course.get("modules");
            if (!(modulesObj instanceof List<?> modules)) {
                return 0;
            }
            int total = 0;
            for (Object moduleObj : modules) {
                if (moduleObj instanceof Map<?, ?> module) {
                    Object lessonsObj = module.get("lessons");
                    if (lessonsObj instanceof List<?> lessons) {
                        total += lessons.size();
                    }
                }
            }
            return total;
        } catch (Exception e) {
            return 0;
        }
    }

    private String resolveLessonTitle(Long courseId, Long lessonId) {
        try {
            Map<?, ?> course = restTemplate.getForObject("http://course-service/courses/" + courseId, Map.class);
            if (course == null) {
                return "Lesson " + lessonId;
            }
            Object modulesObj = course.get("modules");
            if (modulesObj instanceof List<?> modules) {
                for (Object moduleObj : modules) {
                    if (moduleObj instanceof Map<?, ?> module && module.get("lessons") instanceof List<?> lessons) {
                        for (Object lessonObj : lessons) {
                            if (lessonObj instanceof Map<?, ?> lesson) {
                                Object idObj = lesson.get("id");
                                if (idObj != null && Long.valueOf(idObj.toString()).equals(lessonId)) {
                                    Object titleObj = lesson.get("title");
                                    return titleObj != null ? titleObj.toString() : "Lesson " + lessonId;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "Lesson " + lessonId;
    }

    private void issueCertificate(Enrollment enrollment) {
        certificateRepository.findByUserIdAndCourseId(enrollment.getUserId(), enrollment.getCourseId())
                .orElseGet(() -> {
                    Certificate certificate = new Certificate();
                    certificate.setUserId(enrollment.getUserId());
                    certificate.setCourseId(enrollment.getCourseId());
                    populateCourseDetails(certificate, enrollment.getCourseId());
                    certificate.setCertificateNo("CERT-" + enrollment.getCourseId() + "-" + enrollment.getUserId());
                    certificate.setVerificationCode(UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
                    Certificate saved = certificateRepository.save(certificate);
                    enrollment.setCertificateIssued(Boolean.TRUE);
                    if (enrollment.getCompletedAt() == null) {
                        enrollment.setCompletedAt(LocalDateTime.now());
                    }
                    repository.save(enrollment);
                    publishCertificateEvent(saved);
                    return saved;
                });
    }

    private void populateCourseDetails(Certificate certificate, Long courseId) {
        try {
            Map<?, ?> course = restTemplate.getForObject("http://course-service/courses/" + courseId, Map.class);
            if (course != null) {
                Object titleObj = course.get("title");
                certificate.setCourseTitle(titleObj != null ? String.valueOf(titleObj) : "EduLearn Course");
                Object instructorName = course.get("instructorName");
                if (instructorName != null) {
                    certificate.setInstructorName(instructorName.toString());
                }
                return;
            }
        } catch (Exception ignored) {
        }
        certificate.setCourseTitle("EduLearn Course #" + courseId);
        certificate.setInstructorName("EduLearn Instructor");
    }

    private void publishCertificateEvent(Certificate certificate) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "CERTIFICATE_ISSUED");
            event.put("userId", certificate.getUserId());
            event.put("courseId", certificate.getCourseId());
            event.put("courseTitle", certificate.getCourseTitle());
            event.put("certificateNo", certificate.getCertificateNo());
            kafkaTemplate.send("course-events", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Error publishing certificate event: {}", e.getMessage(), e);
        }
    }

    private LessonStatusResponse mapLessonProgress(LessonProgress lessonProgress) {
        LessonStatusResponse response = new LessonStatusResponse();
        response.setLessonId(lessonProgress.getLessonId());
        response.setLessonTitle(lessonProgress.getLessonTitle());
        response.setPercentComplete(lessonProgress.getPercentComplete());
        response.setStatus(lessonProgress.getStatus());
        response.setCompletedAt(lessonProgress.getCompletedAt());
        return response;
    }

    private CertificateResponse mapCertificate(Certificate certificate) {
        CertificateResponse response = new CertificateResponse();
        response.setId(certificate.getId());
        response.setUserId(certificate.getUserId());
        response.setCourseId(certificate.getCourseId());
        response.setCourseTitle(certificate.getCourseTitle());
        response.setInstructorName(certificate.getInstructorName());
        response.setCertificateNo(certificate.getCertificateNo());
        response.setVerificationCode(certificate.getVerificationCode());
        response.setIssuedAt(certificate.getIssuedAt());
        return response;
    }
}
