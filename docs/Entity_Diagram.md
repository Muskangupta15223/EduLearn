# EduLearn — Entity Relationship Diagram

## Full ER Diagram

```mermaid
erDiagram
    AUTH_USERS {
        Long id PK
        String email UK
        String name
        String provider
        String passwordHash
        String role
        String otp
        LocalDateTime otpExpiresAt
        String sessionToken
    }

    USER_PROFILES {
        Long id PK
        String fullName
        String email UK
    }

    COURSES {
        Long id PK
        String title
        String description
        BigDecimal price
        String category
        String status
        String instructorId
        String videoUrl
    }

    COURSE_MODULES {
        Long id PK
        String title
        Long courseId FK
        Integer orderIndex
    }

    LESSONS {
        Long id PK
        String title
        String type
        String contentUrl
        Long module_id FK
    }

    ASSIGNMENTS {
        Long id PK
        String title
        String description
        Long courseId FK
        LocalDateTime dueDate
    }

    QUIZZES {
        Long id PK
        String title
        Long courseId FK
    }

    QUESTIONS {
        Long id PK
        String text
        String optionA
        String optionB
        String optionC
        String optionD
        String correctOption
        Long quiz_id FK
    }

    ENROLLMENTS {
        Long id PK
        Long userId FK
        Long courseId FK
        String status
        LocalDateTime enrolledAt
    }

    SUBMISSIONS {
        Long id PK
        Long assignmentId FK
        Long userId
        String fileUrl
        Integer marksObtained
        String feedback
        LocalDateTime submittedAt
    }

    QUIZ_ATTEMPTS {
        Long id PK
        Long quizId FK
        Long userId
        Integer score
        Integer totalQuestions
        LocalDateTime attemptedAt
    }

    PAYMENT_TRANSACTIONS {
        Long id PK
        Long userId FK
        Long courseId FK
        Long enrollmentId FK
        String instructorId
        BigDecimal amount
        String currency
        PaymentStatus status
        String gatewayTransactionId UK
        String razorpayOrderId UK
        String idempotencyKey UK
        String failureReason
        LocalDateTime createdAt
        LocalDateTime updatedAt
    }

    NOTIFICATION_LOGS {
        Long id PK
        String recipient
        String message
        LocalDateTime sentAt
    }

    %% Relationships
    COURSES ||--o{ COURSE_MODULES : "has"
    COURSE_MODULES ||--o{ LESSONS : "contains"
    COURSES ||--o{ ASSIGNMENTS : "has"
    COURSES ||--o{ QUIZZES : "has"
    QUIZZES ||--o{ QUESTIONS : "contains"
    COURSES ||--o{ ENROLLMENTS : "enrolled in"
    AUTH_USERS ||--o{ ENROLLMENTS : "enrolls"
    ASSIGNMENTS ||--o{ SUBMISSIONS : "submitted for"
    QUIZZES ||--o{ QUIZ_ATTEMPTS : "attempted in"
    ENROLLMENTS ||--o| PAYMENT_TRANSACTIONS : "paid via"
```

## Database-per-Service Mapping

| Service | Database | Tables |
|---------|----------|--------|
| Auth Service | auth_db | auth_users |
| User Service | user_db | user_profiles |
| Course Service | course_db | courses, course_modules, lessons, assignments, quizzes, questions |
| Enrollment Service | enrollment_db | enrollments, submissions, quiz_attempts |
| Payment Service | payment_db | payment_transactions |
| Notification Service | notification_db | notification_logs |
