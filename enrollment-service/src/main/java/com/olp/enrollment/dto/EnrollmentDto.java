package com.olp.enrollment.dto;

import java.time.LocalDateTime;

public record EnrollmentDto(
    Long id,
    Long userId,
    Long courseId,
    String status,
    Integer progress,
    String lastLesson,
    LocalDateTime completedAt,
    Boolean certificateIssued,
    LocalDateTime enrolledAt
) {}
