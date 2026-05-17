package com.olp.course.dto;

import com.olp.course.model.Assignment;
import com.olp.course.model.AssignmentSubmission;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AssignmentDtos {
    private AssignmentDtos() {
    }

    public record AssignmentRequest(String title, String description, LocalDate dueDate, Integer maxScore) {
        public Assignment toEntity() {
            Assignment assignment = new Assignment();
            assignment.setTitle(title);
            assignment.setDescription(description);
            assignment.setDueDate(dueDate);
            assignment.setMaxScore(maxScore);
            return assignment;
        }
    }

    public record AssignmentResponse(Long id, Long courseId, String title, String description, LocalDate dueDate, Integer maxScore) {
        public static AssignmentResponse from(Assignment assignment) {
            return new AssignmentResponse(
                    assignment.getId(),
                    assignment.getCourseId(),
                    assignment.getTitle(),
                    assignment.getDescription(),
                    assignment.getDueDate(),
                    assignment.getMaxScore()
            );
        }
    }

    public record AssignmentSubmissionRequest(String submissionText, String fileUrl) {
    }

    public record AssignmentSubmissionResponse(
            Long id,
            Long userId,
            Long assignmentId,
            String submissionText,
            String fileUrl,
            Integer score,
            String feedback,
            String status,
            LocalDateTime submittedAt
    ) {
        public static AssignmentSubmissionResponse from(AssignmentSubmission submission) {
            Long assignmentId = submission.getAssignment() == null ? null : submission.getAssignment().getId();
            return new AssignmentSubmissionResponse(
                    submission.getId(),
                    submission.getUserId(),
                    assignmentId,
                    submission.getSubmissionText(),
                    submission.getFileUrl(),
                    submission.getScore(),
                    submission.getFeedback(),
                    submission.getStatus(),
                    submission.getSubmittedAt()
            );
        }
    }

    public record GradeSubmissionRequest(Integer score, String feedback) {
    }

    public static List<AssignmentResponse> toAssignmentResponses(List<Assignment> assignments) {
        return assignments.stream().map(AssignmentResponse::from).toList();
    }

    public static List<AssignmentSubmissionResponse> toSubmissionResponses(List<AssignmentSubmission> submissions) {
        return submissions.stream().map(AssignmentSubmissionResponse::from).toList();
    }
}
