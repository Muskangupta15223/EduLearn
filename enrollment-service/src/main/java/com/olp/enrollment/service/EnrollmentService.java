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
import java.awt.geom.RoundRectangle2D;
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
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String STATUS_STARTED = "STARTED";
    private static final String DEFAULT_INSTRUCTOR_NAME = "EduLearn Instructor";
    private static final String DEFAULT_COURSE_TITLE = "EduLearn Course";
    private static final String LESSON_TITLE_PREFIX = "Lesson ";

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
                current.setStatus(STATUS_ACTIVE);
            } else if (current.getStatus() == null) {
                current.setStatus(STATUS_PENDING_PAYMENT);
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
            if (STATUS_ACTIVE.equals(savedExisting.getStatus())) {
                publishEnrollmentEvent(savedExisting);
            }
            return savedExisting;
        }

        enrollment.setEnrolledAt(LocalDateTime.now());
        if (enrollment.getStatus() == null) {
            enrollment.setStatus(isFree ? STATUS_ACTIVE : STATUS_PENDING_PAYMENT);
        }
        if (enrollment.getProgress() == null) {
            enrollment.setProgress(0);
        }
        Enrollment saved = repository.save(enrollment);
        if (STATUS_ACTIVE.equals(saved.getStatus())) {
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
        return repository.findByUserIdAndCourseId(userId, courseId)
                .map(enrollment -> !STATUS_PENDING_PAYMENT.equalsIgnoreCase(enrollment.getStatus()))
                .orElse(false);
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
            LessonProgress lessonProgress = updateLessonProgress(userId, courseId, lastLesson, resolvedPercent);
            enrollment.setLastLesson(lessonProgress.getLessonTitle() != null ? lessonProgress.getLessonTitle() : lastLesson);
        }

        enrollment.setProgress(calculateCourseProgress(userId, courseId, resolvedPercent));
        completeEnrollmentIfNeeded(enrollment);
        return repository.save(enrollment);
    }

    public Optional<Enrollment> updateStatus(Long id, String status) {
        return repository.findById(id).map(enrollment -> {
            enrollment.setStatus(status);
            if (STATUS_COMPLETED.equalsIgnoreCase(status) && enrollment.getCompletedAt() == null) {
                enrollment.setCompletedAt(LocalDateTime.now());
            }
            Enrollment saved = repository.save(enrollment);
            if (STATUS_ACTIVE.equals(status)) {
                publishEnrollmentEvent(saved);
            }
            return saved;
        });
    }

    public Optional<Enrollment> markComplete(Long userId, Long courseId) {
        return repository.findByUserIdAndCourseId(userId, courseId).map(enrollment -> {
            enrollment.setProgress(100);
            enrollment.setStatus(STATUS_COMPLETED);
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
        if ((enrollment.getProgress() != null && enrollment.getProgress() >= 100) || STATUS_COMPLETED.equals(enrollment.getStatus())) {
            issueCertificate(enrollment);
        }
        return certificateRepository.findByUserIdAndCourseId(userId, courseId).map(this::mapCertificate);
    }

    public Optional<byte[]> generateCertificateImage(Long userId, Long courseId, String userName) {
        return getCertificate(userId, courseId).map(cert -> {
            try {
                int width = 1600;
                int height = 1100;
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = image.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                String displayName = (userName != null && !userName.isBlank()) ? userName : "Learner #" + cert.getUserId();
                String instructor = cert.getInstructorName() != null ? cert.getInstructorName() : DEFAULT_INSTRUCTOR_NAME;
                String issuedDate = cert.getIssuedAt() != null
                        ? cert.getIssuedAt().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))
                        : "N/A";

                Color bgTop = new Color(247, 241, 230);
                Color bgBottom = new Color(255, 252, 246);
                Color framePrimary = new Color(163, 116, 61);
                Color frameSecondary = new Color(102, 72, 38);
                Color textPrimary = new Color(49, 36, 20);
                Color textMuted = new Color(118, 92, 61);
                Color accent = new Color(14, 116, 144);
                Color sealFill = new Color(191, 149, 63);
                Color sealStroke = new Color(122, 85, 33);

                g.setPaint(new GradientPaint(0, 0, bgTop, 0, height, bgBottom));
                g.fillRect(0, 0, width, height);

                g.setColor(new Color(255, 255, 255, 205));
                g.fill(new RoundRectangle2D.Double(80, 72, width - 160, height - 144, 34, 34));

                g.setColor(framePrimary);
                g.setStroke(new BasicStroke(10f));
                g.draw(new RoundRectangle2D.Double(58, 50, width - 116, height - 100, 42, 42));

                g.setColor(frameSecondary);
                g.setStroke(new BasicStroke(3f));
                g.draw(new RoundRectangle2D.Double(86, 78, width - 172, height - 156, 30, 30));

                g.setColor(new Color(229, 214, 188));
                g.fillOval(110, 95, 140, 140);
                g.fillOval(width - 250, 95, 140, 140);
                g.fillOval(110, height - 235, 140, 140);
                g.fillOval(width - 250, height - 235, 140, 140);

                g.setColor(framePrimary);
                g.setFont(new Font("SansSerif", Font.BOLD, 34));
                drawCenteredString(g, "EDULEARN LMS", width, 160);

                g.setColor(frameSecondary);
                g.setFont(new Font("Serif", Font.BOLD, 66));
                drawCenteredString(g, "Certificate of Completion", width, 245);

                g.setColor(textMuted);
                g.setFont(new Font("SansSerif", Font.PLAIN, 28));
                drawCenteredString(g, "This certifies that", width, 320);

                g.setColor(textPrimary);
                g.setFont(new Font("Serif", Font.BOLD, 74));
                drawCenteredString(g, displayName, width, 415);

                g.setColor(textMuted);
                g.setFont(new Font("SansSerif", Font.PLAIN, 28));
                drawCenteredString(g, "has successfully completed the course", width, 485);

                g.setColor(accent);
                g.setFont(new Font("Serif", Font.BOLD, 54));
                drawCenteredParagraph(g, cert.getCourseTitle(), width / 2, 555, 980, 66);

                g.setColor(textMuted);
                g.setFont(new Font("SansSerif", Font.PLAIN, 24));
                drawCenteredString(g, "Awarded in recognition of dedication, consistency, and successful completion.", width, 705);

                int signatureY = 830;
                int lineWidth = 280;
                int leftX = 250;
                int rightX = width - 250 - lineWidth;
                g.setColor(framePrimary);
                g.setStroke(new BasicStroke(2f));
                g.drawLine(leftX, signatureY, leftX + lineWidth, signatureY);
                g.drawLine(rightX, signatureY, rightX + lineWidth, signatureY);

                g.setColor(textPrimary);
                g.setFont(new Font("Serif", Font.BOLD, 30));
                drawCenteredStringAt(g, instructor, leftX + (lineWidth / 2), signatureY + 44);
                drawCenteredStringAt(g, issuedDate, rightX + (lineWidth / 2), signatureY + 44);

                g.setColor(textMuted);
                g.setFont(new Font("SansSerif", Font.PLAIN, 19));
                drawCenteredStringAt(g, "Instructor", leftX + (lineWidth / 2), signatureY + 78);
                drawCenteredStringAt(g, "Date Issued", rightX + (lineWidth / 2), signatureY + 78);

                int sealSize = 152;
                int sealX = (width - sealSize) / 2;
                int sealY = 770;
                g.setColor(sealFill);
                g.fillOval(sealX, sealY, sealSize, sealSize);
                g.setColor(sealStroke);
                g.setStroke(new BasicStroke(5f));
                g.drawOval(sealX + 6, sealY + 6, sealSize - 12, sealSize - 12);
                g.setFont(new Font("SansSerif", Font.BOLD, 24));
                drawCenteredStringAt(g, "VERIFIED", width / 2, sealY + 62);
                g.setFont(new Font("SansSerif", Font.BOLD, 20));
                drawCenteredStringAt(g, "ACHIEVEMENT", width / 2, sealY + 94);

                g.setColor(textMuted);
                g.setFont(new Font("Monospaced", Font.PLAIN, 18));
                g.drawString("Certificate ID: " + cert.getCertificateNo(), 110, height - 98);
                g.drawString("Verification Code: " + cert.getVerificationCode(), 110, height - 66);

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

    private LessonProgress updateLessonProgress(Long userId, Long courseId, String lastLesson, Integer resolvedPercent) {
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
        updateLessonProgressStatus(lessonProgress, resolvedPercent);
        return lessonProgressRepository.save(lessonProgress);
    }

    private void updateLessonProgressStatus(LessonProgress lessonProgress, Integer resolvedPercent) {
        if (resolvedPercent >= 100) {
            lessonProgress.setStatus(STATUS_COMPLETED);
            lessonProgress.setCompletedAt(LocalDateTime.now());
            return;
        }
        if (resolvedPercent > 0) {
            lessonProgress.setStatus(STATUS_IN_PROGRESS);
            return;
        }
        lessonProgress.setStatus(STATUS_STARTED);
    }

    private void completeEnrollmentIfNeeded(Enrollment enrollment) {
        if (enrollment.getProgress() < 100) {
            return;
        }
        enrollment.setStatus(STATUS_COMPLETED);
        enrollment.setCompletedAt(LocalDateTime.now());
        issueCertificate(enrollment);
    }

    private void drawCenteredString(Graphics2D g, String text, int width, int y) {
        FontMetrics fm = g.getFontMetrics();
        int x = (width - fm.stringWidth(text)) / 2;
        g.drawString(text, x, y);
    }

    private void drawCenteredStringAt(Graphics2D g, String text, int centerX, int baselineY) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, centerX - (fm.stringWidth(text) / 2), baselineY);
    }

    private void drawCenteredParagraph(Graphics2D g, String text, int centerX, int startY, int maxWidth, int lineHeight) {
        List<String> lines = wrapText(g, text, maxWidth);
        int totalHeight = Math.max(0, (lines.size() - 1) * lineHeight);
        int y = startY - (totalHeight / 2);
        for (String line : lines) {
            drawCenteredStringAt(g, line, centerX, y);
            y += lineHeight;
        }
    }

    private List<String> wrapText(Graphics2D g, String text, int maxWidth) {
        if (text == null || text.isBlank()) {
            return List.of("");
        }

        List<String> lines = new java.util.ArrayList<>();
        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder();
        FontMetrics metrics = g.getFontMetrics();

        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (metrics.stringWidth(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                }
                current.setLength(0);
                current.append(word);
            }
        }

        if (!current.isEmpty()) {
            lines.add(current.toString());
        }

        return lines;
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
                    cert.getInstructorName() != null ? cert.getInstructorName() : DEFAULT_INSTRUCTOR_NAME,
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
            CourseDetailsResponse course = fetchCourseDetails(courseId);
            if (course == null) {
                return;
            }
            if (course.title() != null) {
                event.put("courseTitle", course.title());
            }
            if (course.instructorId() != null) {
                event.put("instructorId", course.instructorId());
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
            CourseDetailsResponse course = fetchCourseDetails(courseId);
            if (course != null) {
                Double price = course.price();
                if (price == null) return true;
                return price <= 0;
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
            CourseDetailsResponse course = fetchCourseDetails(courseId);
            if (course == null) {
                return 0;
            }
            int total = 0;
            for (ModuleDetailsResponse module : safeModules(course.modules())) {
                total += safeLessons(module.lessons()).size();
            }
            return total;
        } catch (Exception e) {
            return 0;
        }
    }

    private String resolveLessonTitle(Long courseId, Long lessonId) {
        try {
            CourseDetailsResponse course = fetchCourseDetails(courseId);
            if (course == null) {
                return LESSON_TITLE_PREFIX + lessonId;
            }
            for (ModuleDetailsResponse module : safeModules(course.modules())) {
                for (LessonDetailsResponse lesson : safeLessons(module.lessons())) {
                    if (lesson.id() != null && lesson.id().equals(lessonId)) {
                        return lesson.title() != null ? lesson.title() : LESSON_TITLE_PREFIX + lessonId;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return LESSON_TITLE_PREFIX + lessonId;
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

    private CourseDetailsResponse fetchCourseDetails(Long courseId) {
        return restTemplate.getForObject("http://course-service/courses/" + courseId, CourseDetailsResponse.class);
    }

    private List<ModuleDetailsResponse> safeModules(List<ModuleDetailsResponse> modules) {
        return modules == null ? List.of() : modules;
    }

    private List<LessonDetailsResponse> safeLessons(List<LessonDetailsResponse> lessons) {
        return lessons == null ? List.of() : lessons;
    }

    private void populateCourseDetails(Certificate certificate, Long courseId) {
        try {
            CourseDetailsResponse course = fetchCourseDetails(courseId);
            if (course != null) {
                certificate.setCourseTitle(course.title() != null ? course.title() : DEFAULT_COURSE_TITLE);
                if (course.instructorName() != null) {
                    certificate.setInstructorName(course.instructorName());
                }
                return;
            }
        } catch (Exception ignored) {
        }
        certificate.setCourseTitle(DEFAULT_COURSE_TITLE + " #" + courseId);
        certificate.setInstructorName(DEFAULT_INSTRUCTOR_NAME);
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

    private record CourseDetailsResponse(
            String title,
            Long instructorId,
            String instructorName,
            Double price,
            List<ModuleDetailsResponse> modules
    ) {
    }

    private record ModuleDetailsResponse(List<LessonDetailsResponse> lessons) {
    }

    private record LessonDetailsResponse(Long id, String title) {
    }
}
