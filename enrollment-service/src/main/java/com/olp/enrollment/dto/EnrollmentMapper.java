package com.olp.enrollment.dto;

import com.olp.enrollment.model.Enrollment;
import java.util.List;
import java.util.stream.Collectors;

public class EnrollmentMapper {
    private EnrollmentMapper() {}

    public static EnrollmentDto toDto(Enrollment enrollment) {
        if (enrollment == null) return null;
        
        return new EnrollmentDto(
            enrollment.getId(),
            enrollment.getUserId(),
            enrollment.getCourseId(),
            enrollment.getStatus(),
            enrollment.getProgress(),
            enrollment.getLastLesson(),
            enrollment.getCompletedAt(),
            enrollment.getCertificateIssued(),
            enrollment.getEnrolledAt()
        );
    }

    public static List<EnrollmentDto> toDtoList(List<Enrollment> enrollments) {
        if (enrollments == null) return List.of();
        return enrollments.stream().map(EnrollmentMapper::toDto).collect(Collectors.toList());
    }
}
