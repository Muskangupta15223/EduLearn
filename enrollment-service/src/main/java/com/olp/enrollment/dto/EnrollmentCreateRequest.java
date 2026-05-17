package com.olp.enrollment.dto;

import com.olp.enrollment.model.Enrollment;

public record EnrollmentCreateRequest(Long userId, Long courseId, String status, Integer progress) {
    public Enrollment toEntity() {
        Enrollment enrollment = new Enrollment();
        enrollment.setUserId(userId);
        enrollment.setCourseId(courseId);
        enrollment.setStatus(status);
        enrollment.setProgress(progress);
        return enrollment;
    }
}
