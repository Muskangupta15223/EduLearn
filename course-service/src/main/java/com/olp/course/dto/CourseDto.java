package com.olp.course.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CourseDto(
    Long id,
    String title,
    String description,
    BigDecimal price,
    Long instructorId,
    String instructorName,
    String category,
    String level,
    String language,
    String status,
    String reviewStatus,
    String reviewComment,
    String thumbnail,
    Double rating,
    Integer studentsCount,
    String duration,
    Boolean instructorVerified,
    String instructorVerificationStatus,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
