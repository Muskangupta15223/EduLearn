package com.olp.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.notification.model.NotificationLog;
import com.olp.notification.repository.NotificationLogRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class KafkaConsumerService {

    private final NotificationLogRepository repository;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final RestTemplate restTemplate;

    public KafkaConsumerService(NotificationLogRepository repository, ObjectMapper objectMapper, 
                                EmailService emailService, RestTemplate restTemplate) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.emailService = emailService;
        this.restTemplate = restTemplate;
    }

    @KafkaListener(topics = "user-events", groupId = "notification-group")
    public void consumeUserEvents(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventType = event.get("eventType").asText();

            if ("USER_SIGNUP".equals(eventType)) {
                String email = event.get("email").asText();
                String fullName = event.get("fullName").asText();
                Long userId = event.get("userId").asLong();

                // 1. Send Welcome Email (async, non-blocking)
                emailService.sendWelcomeEmail(email, fullName);

                // 2. Log notification in Database
                NotificationLog log = new NotificationLog();
                log.setUserId(userId);
                log.setType("welcome");
                log.setTitle("Welcome to EduLearn");
                log.setMessage("Hi " + fullName + ", welcome to our platform! Start exploring courses today.");
                log.setSentAt(LocalDateTime.now());
                log.setIsRead(false);
                log.setRecipient(email);
                repository.save(log);

            } else if ("USER_LOGIN".equals(eventType)) {
                Long userId = event.path("userId").asLong(0L);
                String fullName = event.path("fullName").asText("there");

                NotificationLog log = new NotificationLog();
                log.setUserId(userId);
                log.setType("login");
                log.setTitle("Login Successful");
                log.setMessage("Hi " + fullName + ", you just logged in to EduLearn.");
                log.setSentAt(LocalDateTime.now());
                log.setIsRead(false);
                log.setRecipient(event.path("email").asText(null));
                repository.save(log);

            } else if ("PASSWORD_RESET".equals(eventType)) {
                String email = event.get("email").asText();
                String fullName = event.get("fullName").asText();
                String resetLink = event.get("resetLink").asText();

                // Send password reset email
                emailService.sendPasswordResetEmail(email, fullName, resetLink);
            }
        } catch (Exception e) {
            System.err.println("Error processing User Kafka message: " + e.getMessage());
        }
    }

    @KafkaListener(topics = "course-events", groupId = "notification-group")
    public void consumeCourseEvents(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventType = event.get("eventType").asText();

            if ("STUDENT_ENROLLED".equals(eventType)) {
                Long courseId = event.get("courseId").asLong();
                Long studentId = event.get("userId").asLong();

                String courseName = event.path("courseTitle").asText("");
                Long instructorId = event.hasNonNull("instructorId") ? event.get("instructorId").asLong() : null;
                if (courseName.isBlank() || instructorId == null) {
                    if (courseName.isBlank()) {
                        courseName = "a course";
                    }
                    if (instructorId == null) {
                        instructorId = 1L;
                    }
                    try {
                        ResponseEntity<Map> response = restTemplate.getForEntity(
                            "http://course-service/courses/" + courseId, Map.class);
                        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                            Map<String, Object> courseData = response.getBody();
                            if (courseData.get("title") != null) courseName = courseData.get("title").toString();
                            if (courseData.get("instructorId") != null) {
                                instructorId = Long.valueOf(courseData.get("instructorId").toString());
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to fetch course details for notification: " + e.getMessage());
                    }
                }

                // Notify the student about successful enrollment
                NotificationLog studentLog = new NotificationLog();
                studentLog.setUserId(studentId);
                studentLog.setType("enrollment");
                studentLog.setTitle("Enrollment Confirmed!");
                studentLog.setMessage("You have been successfully enrolled in \"" + courseName + "\". Start learning now!");
                studentLog.setSentAt(LocalDateTime.now());
                studentLog.setIsRead(false);
                repository.save(studentLog);

                // Notify instructor
                NotificationLog instructorLog = new NotificationLog();
                instructorLog.setUserId(instructorId);
                instructorLog.setType("enrollment");
                instructorLog.setTitle("New Student Enrollment");
                instructorLog.setMessage("A new student has enrolled in \"" + courseName + "\" (Course ID: " + courseId + ")");
                instructorLog.setSentAt(LocalDateTime.now());
                instructorLog.setIsRead(false);
                repository.save(instructorLog);
            } else if ("CERTIFICATE_ISSUED".equals(eventType)) {
                Long userId = event.get("userId").asLong();
                String courseTitle = event.has("courseTitle") ? event.get("courseTitle").asText() : "your course";
                String certificateNo = event.has("certificateNo") ? event.get("certificateNo").asText() : "";

                NotificationLog certificateLog = new NotificationLog();
                certificateLog.setUserId(userId);
                certificateLog.setType("certificate");
                certificateLog.setTitle("Certificate Ready");
                certificateLog.setMessage("Your certificate for \"" + courseTitle + "\" is now available" +
                        (certificateNo.isBlank() ? "." : " (Certificate No: " + certificateNo + ")."));
                certificateLog.setSentAt(LocalDateTime.now());
                certificateLog.setIsRead(false);
                repository.save(certificateLog);
            } else if ("COURSE_APPROVAL_REQUEST".equals(eventType)) {
                String courseTitle = event.path("title").asText("Untitled Course");
                String instructorName = event.path("instructorName").asText("an instructor");
                String action = event.path("action").asText("SUBMITTED");
                String timestampStr = event.path("timestamp").asText("");
                
                LocalDateTime sentAt = LocalDateTime.now();
                if (!timestampStr.isEmpty()) {
                    try {
                        sentAt = LocalDateTime.parse(timestampStr);
                    } catch (Exception ignored) {}
                }

                String title;
                String adminMessage;
                if ("CREATED".equalsIgnoreCase(action)) {
                    title = "New Course Draft Created";
                    adminMessage = "Instructor " + instructorName + " created the course \"" + courseTitle + "\". Review when ready for approval.";
                } else if ("UPDATED".equalsIgnoreCase(action)) {
                    title = "Course Draft Updated";
                    adminMessage = "Instructor " + instructorName + " updated the course \"" + courseTitle + "\". Review the latest changes for approval.";
                } else {
                    title = "Course Review Requested";
                    adminMessage = "Instructor " + instructorName + " has submitted the course \"" + courseTitle + "\" for review.";
                }

                for (Long adminId : resolveAdminUserIds()) {
                    NotificationLog adminLog = new NotificationLog();
                    adminLog.setUserId(adminId);
                    adminLog.setType("COURSE_APPROVAL_REQUEST");
                    adminLog.setTitle(title);
                    adminLog.setMessage(adminMessage);
                    adminLog.setSentAt(sentAt);
                    adminLog.setIsRead(false);
                    repository.save(adminLog);
                }
            } else if ("COURSE_APPROVED".equals(eventType) || "COURSE_REJECTED".equals(eventType) || "COURSE_UNPUBLISHED".equals(eventType)) {
                Long instructorId = event.path("instructorId").asLong(0L);
                String courseTitle = event.path("title").asText("your course");
                String reviewComment = event.path("reviewComment").asText("");

                NotificationLog instructorLog = new NotificationLog();
                instructorLog.setUserId(instructorId);
                instructorLog.setType("course");
                if ("COURSE_APPROVED".equals(eventType)) {
                    instructorLog.setTitle("Course Approved");
                    instructorLog.setMessage("\"" + courseTitle + "\" has been approved and published.");
                } else if ("COURSE_REJECTED".equals(eventType)) {
                    instructorLog.setTitle("Course Needs Changes");
                    instructorLog.setMessage("\"" + courseTitle + "\" was rejected. " + reviewComment);
                } else {
                    instructorLog.setTitle("Course Unpublished");
                    instructorLog.setMessage("\"" + courseTitle + "\" was moved back to draft.");
                }
                instructorLog.setSentAt(LocalDateTime.now());
                instructorLog.setIsRead(false);
                repository.save(instructorLog);

            } else if ("QUIZ_CREATED".equals(eventType) || "ASSIGNMENT_CREATED".equals(eventType)) {
                Long courseId = event.get("courseId").asLong();

                // Fetch course title from course-service
                String courseName = event.path("courseTitle").asText("");
                if (courseName.isBlank()) {
                    courseName = "a course";
                    try {
                        ResponseEntity<Map> courseResponse = restTemplate.getForEntity(
                            "http://course-service/courses/" + courseId, Map.class);
                        if (courseResponse.getStatusCode().is2xxSuccessful() && courseResponse.getBody() != null) {
                            Object titleObj = courseResponse.getBody().get("title");
                            if (titleObj != null) courseName = titleObj.toString();
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to fetch course title for notification: " + e.getMessage());
                    }
                }

                // Fetch enrolled students from enrollment-service
                java.util.List<Long> enrolledStudentIds = new java.util.ArrayList<>();
                try {
                    ResponseEntity<java.util.List> enrollResponse = restTemplate.getForEntity(
                        "http://enrollment-service/enrollments/course/" + courseId, java.util.List.class);
                    if (enrollResponse.getStatusCode().is2xxSuccessful() && enrollResponse.getBody() != null) {
                        for (Object enrollObj : enrollResponse.getBody()) {
                            if (enrollObj instanceof Map) {
                                Map<?, ?> enrollMap = (Map<?, ?>) enrollObj;
                                Object statusObj = enrollMap.get("status");
                                Object userIdObj = enrollMap.get("userId");
                                if (userIdObj != null && ("ACTIVE".equals(statusObj) || "COMPLETED".equals(statusObj))) {
                                    enrolledStudentIds.add(Long.valueOf(userIdObj.toString()));
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to fetch enrollments for notification: " + e.getMessage());
                }

                // Create notification for each enrolled student
                if ("QUIZ_CREATED".equals(eventType)) {
                    String quizTitle = event.path("quizTitle").asText("a new quiz");
                    for (Long studentId : enrolledStudentIds) {
                        NotificationLog studentLog = new NotificationLog();
                        studentLog.setUserId(studentId);
                        studentLog.setType("quiz");
                        studentLog.setTitle("New Quiz Available");
                        studentLog.setMessage("A new quiz \"" + quizTitle + "\" has been added to \"" + courseName + "\". Test your knowledge now!");
                        studentLog.setSentAt(LocalDateTime.now());
                        studentLog.setIsRead(false);
                        repository.save(studentLog);
                    }
                } else {
                    String assignmentTitle = event.path("assignmentTitle").asText("a new assignment");
                    String dueDate = event.has("dueDate") && !event.get("dueDate").isNull() ? event.get("dueDate").asText() : null;
                    for (Long studentId : enrolledStudentIds) {
                        NotificationLog studentLog = new NotificationLog();
                        studentLog.setUserId(studentId);
                        studentLog.setType("assignment");
                        studentLog.setTitle("New Assignment Posted");
                        String msg = "A new assignment \"" + assignmentTitle + "\" has been added to \"" + courseName + "\".";
                        if (dueDate != null) {
                            msg += " Deadline: " + dueDate + ".";
                        }
                        studentLog.setMessage(msg);
                        studentLog.setSentAt(LocalDateTime.now());
                        studentLog.setIsRead(false);
                        repository.save(studentLog);
                    }
                }

                System.out.println("Notified " + enrolledStudentIds.size() + " enrolled students about " + eventType);
            } else if ("COURSE_CONTENT_ADDED".equals(eventType)) {
                Long courseId = event.get("courseId").asLong();
                String courseName = event.path("courseTitle").asText("your course");
                String contentType = event.path("contentType").asText("content");
                String contentTitle = event.path("contentTitle").asText("new content");

                List<Long> enrolledStudentIds = new ArrayList<>();
                try {
                    ResponseEntity<List> enrollResponse = restTemplate.getForEntity(
                            "http://enrollment-service/enrollments/course/" + courseId, List.class);
                    if (enrollResponse.getStatusCode().is2xxSuccessful() && enrollResponse.getBody() != null) {
                        for (Object enrollObj : enrollResponse.getBody()) {
                            if (enrollObj instanceof Map<?, ?> enrollMap) {
                                Object statusObj = enrollMap.get("status");
                                Object userIdObj = enrollMap.get("userId");
                                boolean active = statusObj != null
                                        && ("ACTIVE".equalsIgnoreCase(statusObj.toString())
                                        || "COMPLETED".equalsIgnoreCase(statusObj.toString()));
                                if (userIdObj != null && active) {
                                    enrolledStudentIds.add(Long.valueOf(userIdObj.toString()));
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to fetch enrollments for content notification: " + e.getMessage());
                }

                String normalizedType = contentType == null || contentType.isBlank() ? "content" : contentType.toLowerCase();
                String displayType = normalizedType.substring(0, 1).toUpperCase() + normalizedType.substring(1);
                for (Long studentId : enrolledStudentIds) {
                    NotificationLog studentLog = new NotificationLog();
                    studentLog.setUserId(studentId);
                    studentLog.setType(normalizedType);
                    studentLog.setTitle("New " + displayType + " Added");
                    studentLog.setMessage("A new " + normalizedType + " \"" + contentTitle + "\" has been added to \"" + courseName + "\".");
                    studentLog.setSentAt(LocalDateTime.now());
                    studentLog.setIsRead(false);
                    repository.save(studentLog);
                }

                System.out.println("Notified " + enrolledStudentIds.size() + " enrolled students about " + eventType);
            } else if ("QUIZ_RESULT".equals(eventType)) {
                Long userId = event.path("userId").asLong(0L);
                String quizTitle = event.path("quizTitle").asText("your quiz");
                int score = event.path("score").asInt(0);
                int maxScore = event.path("maxScore").asInt(0);
                boolean passed = event.path("passed").asBoolean(false);
                boolean timedOut = event.path("timedOut").asBoolean(false);

                NotificationLog quizResultLog = new NotificationLog();
                quizResultLog.setUserId(userId);
                quizResultLog.setType("quiz-result");
                quizResultLog.setTitle(passed ? "Quiz Passed" : "Quiz Result Available");
                String notificationMessage = "Your result for \"" + quizTitle + "\" is ready. Score: " + score + "/" + maxScore + ".";
                if (passed) {
                    notificationMessage += " You passed.";
                } else {
                    notificationMessage += " You did not reach the passing score yet.";
                }
                if (timedOut) {
                    notificationMessage += " The attempt was auto-submitted after the time limit.";
                }
                quizResultLog.setMessage(notificationMessage);
                quizResultLog.setSentAt(LocalDateTime.now());
                quizResultLog.setIsRead(false);
                repository.save(quizResultLog);
            } else if ("ASSIGNMENT_GRADED".equals(eventType)) {
                Long userId = event.path("userId").asLong(0L);
                String assignmentTitle = event.path("assignmentTitle").asText("your assignment");
                String score = event.path("score").asText("");
                String maxScore = event.path("maxScore").asText("");
                String feedback = event.path("feedback").asText("");

                NotificationLog assignmentLog = new NotificationLog();
                assignmentLog.setUserId(userId);
                assignmentLog.setType("assignment");
                assignmentLog.setTitle("Assignment Graded");
                String notificationMessage = "Your assignment \"" + assignmentTitle + "\" has been graded.";
                if (!score.isBlank()) {
                    notificationMessage += " Score: " + score + "/" + maxScore + ".";
                }
                if (!feedback.isBlank()) {
                    notificationMessage += " Feedback: " + feedback;
                }
                assignmentLog.setMessage(notificationMessage);
                assignmentLog.setSentAt(LocalDateTime.now());
                assignmentLog.setIsRead(false);
                repository.save(assignmentLog);
            }
        } catch (Exception e) {
            System.err.println("Error processing Course Kafka message: " + e.getMessage());
        }
    }

    @KafkaListener(topics = "payment-events", groupId = "notification-group")
    public void consumePaymentEvents(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventType = event.path("eventType").asText();
            Long userId = event.path("userId").asLong(0L);
            Long courseId = event.path("courseId").asLong(0L);
            String amount = event.path("amount").asText("");
            String currency = event.path("currency").asText("INR");

            String courseName = "your course";
            try {
                ResponseEntity<Map> response = restTemplate.getForEntity(
                        "http://course-service/courses/" + courseId,
                        Map.class
                );
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Object title = response.getBody().get("title");
                    if (title != null) {
                        courseName = title.toString();
                    }
                }
            } catch (Exception ignored) {
            }

            if ("PAYMENT_SUCCESS".equals(eventType)) {
                NotificationLog log = new NotificationLog();
                log.setUserId(userId);
                log.setType("payment");
                log.setTitle("Payment Successful");
                log.setMessage("Your payment of " + currency + " " + amount + " for \"" + courseName + "\" was successful.");
                log.setSentAt(LocalDateTime.now());
                log.setIsRead(false);
                repository.save(log);
            } else if ("PAYMENT_REFUNDED".equals(eventType)) {
                NotificationLog log = new NotificationLog();
                log.setUserId(userId);
                log.setType("payment");
                log.setTitle("Refund Processed");
                log.setMessage("Your refund for \"" + courseName + "\" has been processed.");
                log.setSentAt(LocalDateTime.now());
                log.setIsRead(false);
                repository.save(log);
            } else if (eventType.startsWith("SUBSCRIPTION_")) {
                String plan = event.path("plan").asText("subscription");
                NotificationLog log = new NotificationLog();
                log.setUserId(userId);
                log.setType("subscription");
                if (eventType.endsWith("_ACTIVATED")) {
                    log.setTitle("Subscription Activated");
                    log.setMessage("Your " + plan + " subscription is now active.");
                } else if ("SUBSCRIPTION_CANCELLED".equals(eventType)) {
                    log.setTitle("Subscription Cancelled");
                    log.setMessage("Your " + plan + " subscription has been cancelled.");
                } else {
                    log.setTitle("Subscription Updated");
                    log.setMessage("Your subscription status changed to " + event.path("status").asText("UPDATED") + ".");
                }
                log.setSentAt(LocalDateTime.now());
                log.setIsRead(false);
                repository.save(log);
            }
        } catch (Exception e) {
            System.err.println("Error processing Payment Kafka message: " + e.getMessage());
        }
    }

    private List<Long> resolveAdminUserIds() {
        try {
            ResponseEntity<List> response = restTemplate.getForEntity("http://user-service/users", List.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return List.of(1L);
            }

            List<Long> adminIds = new ArrayList<>();
            for (Object userObj : response.getBody()) {
                if (userObj instanceof Map<?, ?> userMap) {
                    Object roleObj = userMap.get("role");
                    Object idObj = userMap.get("id");
                    if (roleObj != null && idObj != null && "ADMIN".equalsIgnoreCase(roleObj.toString())) {
                        adminIds.add(Long.valueOf(idObj.toString()));
                    }
                }
            }
            return adminIds.isEmpty() ? List.of(1L) : adminIds;
        } catch (Exception ex) {
            System.err.println("Failed to resolve admin users for notification: " + ex.getMessage());
            return List.of(1L);
        }
    }
}
