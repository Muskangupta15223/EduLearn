package com.olp.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.notification.model.NotificationLog;
import com.olp.notification.repository.NotificationLogRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class KafkaConsumerService {

  private static final Logger log = LoggerFactory.getLogger(
    KafkaConsumerService.class
  );

  private final NotificationLogRepository repository;
  private final ObjectMapper objectMapper;
  private final EmailService emailService;
  private final RestTemplate restTemplate;

  public KafkaConsumerService(
    NotificationLogRepository repository,
    ObjectMapper objectMapper,
    EmailService emailService,
    RestTemplate restTemplate
  ) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.emailService = emailService;
    this.restTemplate = restTemplate;
  }

  @KafkaListener(topics = "user-events", groupId = "notification-group")
  public void consumeUserEvents(String message) {
    try {
      JsonNode event = objectMapper.readTree(message);
      String eventType = event.path("eventType").asText();

      if ("USER_SIGNUP".equals(eventType)) {
        handleUserSignup(event);
      } else if ("USER_LOGIN".equals(eventType)) {
        handleUserLogin(event);
      } else if ("PASSWORD_RESET".equals(eventType)) {
        handlePasswordReset(event);
      }
    } catch (Exception e) {
      log.error("Error processing User Kafka message: {}", e.getMessage(), e);
    }
  }

  private void handleUserSignup(JsonNode event) {
    String email = event.path("email").asText();
    String fullName = event.path("fullName").asText();
    Long userId = event.path("userId").asLong();

    emailService.sendWelcomeEmail(email, fullName);

    NotificationLog log = createNotificationLog(
      userId,
      "welcome",
      "Welcome to EduLearn",
      "Hi " +
      fullName +
      ", welcome to our platform! Start exploring courses today."
    );
    log.setRecipient(email);
    repository.save(log);
  }

  private void handleUserLogin(JsonNode event) {
    Long userId = event.path("userId").asLong(0L);
    String fullName = event.path("fullName").asText("there");
    String email = event.path("email").asText(null);

    NotificationLog log = createNotificationLog(
      userId,
      "login",
      "Login Successful",
      "Hi " + fullName + ", you just logged in to EduLearn."
    );
    log.setRecipient(email);
    repository.save(log);
  }

  private void handlePasswordReset(JsonNode event) {
    String email = event.path("email").asText();
    String fullName = event.path("fullName").asText();
    String resetLink = event.path("resetLink").asText();

    emailService.sendPasswordResetEmail(email, fullName, resetLink);
  }

  @KafkaListener(topics = "course-events", groupId = "notification-group")
  public void consumeCourseEvents(String message) {
    try {
      JsonNode event = objectMapper.readTree(message);
      String eventType = event.path("eventType").asText();

      switch (eventType) {
        case "STUDENT_ENROLLED":
          handleStudentEnrolled(event);
          break;
        case "CERTIFICATE_ISSUED":
          handleCertificateIssued(event);
          break;
        case "COURSE_APPROVAL_REQUEST":
          handleCourseApprovalRequest(event);
          break;
        case "COURSE_APPROVED":
        case "COURSE_REJECTED":
        case "COURSE_UNPUBLISHED":
          handleCourseModeration(event, eventType);
          break;
        case "QUIZ_CREATED":
        case "QUIZ_PUBLISHED":
        case "ASSIGNMENT_CREATED":
          handleContentCreated(event, eventType);
          break;
        case "COURSE_CONTENT_ADDED":
          handleCourseContentAdded(event);
          break;
        case "QUIZ_RESULT":
          handleQuizResult(event);
          break;
        case "ASSIGNMENT_GRADED":
          handleAssignmentGraded(event);
          break;
        default:
          break;
      }
    } catch (Exception e) {
      log.error("Error processing Course Kafka message: {}", e.getMessage(), e);
    }
  }

  private void handleStudentEnrolled(JsonNode event) {
    Long courseId = event.path("courseId").asLong();
    Long studentId = event.path("userId").asLong();

    String courseName = event.path("courseTitle").asText("");
    Long instructorId = event.hasNonNull("instructorId")
      ? event.path("instructorId").asLong()
      : null;

    if (courseName.isBlank() || instructorId == null) {
      if (courseName.isBlank()) courseName = "a course";
      if (instructorId == null) instructorId = 1L;
      try {
        ResponseEntity<Map> response = restTemplate.getForEntity(
          "http://course-service/courses/" + courseId,
          Map.class
        );
        if (
          response.getStatusCode().is2xxSuccessful() &&
          response.getBody() != null
        ) {
          Map<String, Object> courseData = response.getBody();
          if (courseData.get("title") != null) courseName =
            courseData.get("title").toString();
          if (courseData.get("instructorId") != null) {
            instructorId =
              Long.valueOf(courseData.get("instructorId").toString());
          }
        }
      } catch (Exception e) {
        log.warn(
          "Failed to fetch course details for notification: {}",
          e.getMessage()
        );
      }
    }

    NotificationLog studentLog = createNotificationLog(
      studentId,
      "enrollment",
      "Enrollment Confirmed!",
      "You have been successfully enrolled in \"" +
      courseName +
      "\". Start learning now!"
    );
    repository.save(studentLog);

    NotificationLog instructorLog = createNotificationLog(
      instructorId,
      "enrollment",
      "New Student Enrollment",
      "A new student has enrolled in \"" +
      courseName +
      "\" (Course ID: " +
      courseId +
      ")"
    );
    repository.save(instructorLog);
  }

  private void handleCertificateIssued(JsonNode event) {
    Long userId = event.path("userId").asLong();
    String courseTitle = event.path("courseTitle").asText("your course");
    String certificateNo = event.path("certificateNo").asText("");

    String message =
      "Your certificate for \"" +
      courseTitle +
      "\" is now available" +
      (
        certificateNo.isBlank()
          ? "."
          : " (Certificate No: " + certificateNo + ")."
      );
    NotificationLog log = createNotificationLog(
      userId,
      "certificate",
      "Certificate Ready",
      message
    );
    repository.save(log);
  }

  private void handleCourseApprovalRequest(JsonNode event) {
    String courseTitle = event.path("title").asText("Untitled Course");
    String instructorName = event
      .path("instructorName")
      .asText("an instructor");
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
      adminMessage =
        "Instructor " +
        instructorName +
        " created the course \"" +
        courseTitle +
        "\". Review when ready for approval.";
    } else if ("UPDATED".equalsIgnoreCase(action)) {
      title = "Course Draft Updated";
      adminMessage =
        "Instructor " +
        instructorName +
        " updated the course \"" +
        courseTitle +
        "\". Review the latest changes for approval.";
    } else {
      title = "Course Review Requested";
      adminMessage =
        "Instructor " +
        instructorName +
        " has submitted the course \"" +
        courseTitle +
        "\" for review.";
    }

    for (Long adminId : resolveAdminUserIds()) {
      NotificationLog adminLog = createNotificationLog(
        adminId,
        "COURSE_APPROVAL_REQUEST",
        title,
        adminMessage
      );
      adminLog.setSentAt(sentAt);
      repository.save(adminLog);
    }
  }

  private void handleCourseModeration(JsonNode event, String eventType) {
    Long instructorId = event.path("instructorId").asLong(0L);
    String courseTitle = event.path("title").asText("your course");
    String reviewComment = event.path("reviewComment").asText("");

    String title;
    String message;
    if ("COURSE_APPROVED".equals(eventType)) {
      title = "Course Approved";
      message = "\"" + courseTitle + "\" has been approved and published.";
    } else if ("COURSE_REJECTED".equals(eventType)) {
      title = "Course Needs Changes";
      message = "\"" + courseTitle + "\" was rejected. " + reviewComment;
    } else {
      title = "Course Unpublished";
      message = "\"" + courseTitle + "\" was moved back to draft.";
    }

    NotificationLog log = createNotificationLog(
      instructorId,
      "course",
      title,
      message
    );
    repository.save(log);
  }

  private void handleContentCreated(JsonNode event, String eventType) {
    Long courseId = event.path("courseId").asLong();
    String courseName = fetchCourseTitle(event, courseId);
    List<Long> enrolledStudentIds = fetchEnrolledStudents(courseId);

    if ("QUIZ_CREATED".equals(eventType) || "QUIZ_PUBLISHED".equals(eventType)) {
      String quizTitle = event.path("quizTitle").asText("a new quiz");
      String message =
        "A new quiz \"" +
        quizTitle +
        "\" has been added to \"" +
        courseName +
        "\". Test your knowledge now!";
      for (Long studentId : enrolledStudentIds) {
        repository.save(
          createNotificationLog(
            studentId,
            "quiz",
            "New Quiz Available",
            message
          )
        );
      }
    } else {
      String assignmentTitle = event
        .path("assignmentTitle")
        .asText("a new assignment");
      String dueDate = event.path("dueDate").isNull()
        ? null
        : event.path("dueDate").asText();
      String msg =
        "A new assignment \"" +
        assignmentTitle +
        "\" has been added to \"" +
        courseName +
        "\".";
      if (dueDate != null) msg += " Deadline: " + dueDate + ".";
      for (Long studentId : enrolledStudentIds) {
        repository.save(
          createNotificationLog(
            studentId,
            "assignment",
            "New Assignment Posted",
            msg
          )
        );
      }
    }
  }

  private void handleCourseContentAdded(JsonNode event) {
    Long courseId = event.path("courseId").asLong();
    String courseName = event.path("courseTitle").asText("your course");
    String contentType = event.path("contentType").asText("content");
    String contentTitle = event.path("contentTitle").asText("new content");

    List<Long> enrolledStudentIds = fetchEnrolledStudents(courseId);
    String normalizedType = contentType.isBlank()
      ? "content"
      : contentType.toLowerCase();
    String displayType =
      normalizedType.substring(0, 1).toUpperCase() +
      normalizedType.substring(1);
    String message =
      "A new " +
      normalizedType +
      " \"" +
      contentTitle +
      "\" has been added to \"" +
      courseName +
      "\".";

    for (Long studentId : enrolledStudentIds) {
      repository.save(
        createNotificationLog(
          studentId,
          normalizedType,
          "New " + displayType + " Added",
          message
        )
      );
    }
  }

  private void handleQuizResult(JsonNode event) {
    Long userId = event.path("userId").asLong(0L);
    Long instructorId = event.path("instructorId").asLong(0L);
    Long courseId = event.path("courseId").asLong(0L);
    String courseTitle = fetchCourseTitle(event, courseId);
    String quizTitle = event.path("quizTitle").asText("your quiz");
    int score = event.path("score").asInt(0);
    int maxScore = event.path("maxScore").asInt(0);
    boolean passed = event.path("passed").asBoolean(false);
    boolean timedOut = event.path("timedOut").asBoolean(false);

    String title = passed ? "Quiz Passed" : "Quiz Result Available";
    String message =
      "Your result for \"" +
      quizTitle +
      "\" is ready. Score: " +
      score +
      "/" +
      maxScore +
      ".";
    message +=
      passed ? " You passed." : " You did not reach the passing score yet.";
    if (timedOut) message +=
      " The attempt was auto-submitted after the time limit.";

    repository.save(
      createNotificationLog(userId, "quiz-result", title, message)
    );

    if (instructorId > 0) {
      String instructorMessage =
        "Student #" +
        userId +
        " completed the quiz \"" +
        quizTitle +
        "\" in \"" +
        courseTitle +
        "\" with a score of " +
        score +
        "/" +
        maxScore +
        ".";
      if (timedOut) instructorMessage += " The attempt was auto-submitted after the time limit.";

      repository.save(
        createNotificationLog(
          instructorId,
          "quiz-attempt",
          "Student Quiz Submission",
          instructorMessage
        )
      );
    }
  }

  private void handleAssignmentGraded(JsonNode event) {
    Long userId = event.path("userId").asLong(0L);
    String assignmentTitle = event
      .path("assignmentTitle")
      .asText("your assignment");
    String score = event.path("score").asText("");
    String maxScore = event.path("maxScore").asText("");
    String feedback = event.path("feedback").asText("");

    String message =
      "Your assignment \"" + assignmentTitle + "\" has been graded.";
    if (!score.isBlank()) message += " Score: " + score + "/" + maxScore + ".";
    if (!feedback.isBlank()) message += " Feedback: " + feedback;

    repository.save(
      createNotificationLog(userId, "assignment", "Assignment Graded", message)
    );
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

      String courseName = fetchCourseTitle(event, courseId);

      if ("PAYMENT_SUCCESS".equals(eventType)) {
        String msg =
          "Your payment of " +
          currency +
          " " +
          amount +
          " for \"" +
          courseName +
          "\" was successful.";
        repository.save(
          createNotificationLog(userId, "payment", "Payment Successful", msg)
        );
      } else if ("PAYMENT_REFUNDED".equals(eventType)) {
        String msg =
          "Your refund for \"" + courseName + "\" has been processed.";
        repository.save(
          createNotificationLog(userId, "payment", "Refund Processed", msg)
        );
      } else if (eventType.startsWith("SUBSCRIPTION_")) {
        handleSubscriptionEvents(event, eventType, userId);
      }
    } catch (Exception e) {
      log.error(
        "Error processing Payment Kafka message: {}",
        e.getMessage(),
        e
      );
    }
  }

  private void handleSubscriptionEvents(
    JsonNode event,
    String eventType,
    Long userId
  ) {
    String plan = event.path("plan").asText("subscription");
    String title;
    String message;

    if (eventType.endsWith("_ACTIVATED")) {
      title = "Subscription Activated";
      message = "Your " + plan + " subscription is now active.";
    } else if ("SUBSCRIPTION_CANCELLED".equals(eventType)) {
      title = "Subscription Cancelled";
      message = "Your " + plan + " subscription has been cancelled.";
    } else {
      title = "Subscription Updated";
      message =
        "Your subscription status changed to " +
        event.path("status").asText("UPDATED") +
        ".";
    }

    repository.save(
      createNotificationLog(userId, "subscription", title, message)
    );
  }

  private String fetchCourseTitle(JsonNode event, Long courseId) {
    String courseName = event.path("courseTitle").asText("");
    if (courseName.isBlank() && courseId > 0) {
      courseName = "a course";
      try {
        ResponseEntity<Map> response = restTemplate.getForEntity(
          "http://course-service/courses/" + courseId,
          Map.class
        );
        if (
          response.getStatusCode().is2xxSuccessful() &&
          response.getBody() != null
        ) {
          Object titleObj = response.getBody().get("title");
          if (titleObj != null) courseName = titleObj.toString();
        }
      } catch (Exception ignored) {}
    }
    return courseName;
  }

  private List<Long> fetchEnrolledStudents(Long courseId) {
    List<Long> enrolledStudentIds = new ArrayList<>();
    try {
      ResponseEntity<List> response = restTemplate.getForEntity(
        "http://enrollment-service/enrollments/course/" + courseId,
        List.class
      );
      if (
        response.getStatusCode().is2xxSuccessful() && response.getBody() != null
      ) {
        for (Object enrollObj : response.getBody()) {
          if (enrollObj instanceof Map<?, ?> enrollMap) {
            Object statusObj = enrollMap.get("status");
            Object userIdObj = enrollMap.get("userId");
            boolean active =
              statusObj != null &&
              (
                "ACTIVE".equalsIgnoreCase(statusObj.toString()) ||
                "COMPLETED".equalsIgnoreCase(statusObj.toString())
              );
            if (userIdObj != null && active) {
              enrolledStudentIds.add(Long.valueOf(userIdObj.toString()));
            }
          }
        }
      }
    } catch (Exception ignored) {}
    return enrolledStudentIds;
  }

  private List<Long> resolveAdminUserIds() {
    try {
      ResponseEntity<List> response = restTemplate.getForEntity(
        "http://user-service/users",
        List.class
      );
      if (
        !response.getStatusCode().is2xxSuccessful() ||
        response.getBody() == null
      ) {
        return List.of(1L);
      }

      List<Long> adminIds = new ArrayList<>();
      for (Object userObj : response.getBody()) {
        if (userObj instanceof Map<?, ?> userMap) {
          Object roleObj = userMap.get("role");
          Object idObj = userMap.get("id");
          if (
            roleObj != null &&
            idObj != null &&
            "ADMIN".equalsIgnoreCase(roleObj.toString())
          ) {
            adminIds.add(Long.valueOf(idObj.toString()));
          }
        }
      }
      return adminIds.isEmpty() ? List.of(1L) : adminIds;
    } catch (Exception ex) {
      return List.of(1L);
    }
  }

  private NotificationLog createNotificationLog(
    Long userId,
    String type,
    String title,
    String message
  ) {
    NotificationLog log = new NotificationLog();
    log.setUserId(userId);
    log.setType(type);
    log.setTitle(title);
    log.setMessage(message);
    log.setSentAt(LocalDateTime.now());
    log.setIsRead(false);
    return log;
  }
}
