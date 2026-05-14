package com.olp.course.service;

import com.olp.course.model.Assignment;
import com.olp.course.model.AssignmentSubmission;
import com.olp.course.repository.AssignmentRepository;
import com.olp.course.repository.AssignmentSubmissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.course.model.Course;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository submissionRepository;
    private final AccessControlService accessControlService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public AssignmentService(AssignmentRepository assignmentRepository, AssignmentSubmissionRepository submissionRepository,
                             AccessControlService accessControlService,
                             KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.accessControlService = accessControlService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public Assignment createAssignment(Long courseId, Assignment assignment, Long userId, String role) {
        Course course = accessControlService.getOwnedCourse(courseId, userId, role);
        assignment.setCourseId(courseId);
        Assignment saved = assignmentRepository.save(assignment);
        publishAssignmentEvent(saved, course);
        return saved;
    }

    public List<Assignment> getAssignmentsByCourse(Long courseId) {
        return assignmentRepository.findByCourseId(courseId);
    }

    public Optional<Assignment> getAssignmentById(Long id) {
        return assignmentRepository.findById(id);
    }

    public Optional<Assignment> updateAssignment(Long id, Assignment details, Long userId, String role) {
        return assignmentRepository.findById(id).map(assignment -> {
            accessControlService.getOwnedAssignment(id, userId, role);
            assignment.setTitle(details.getTitle());
            assignment.setDescription(details.getDescription());
            assignment.setDueDate(details.getDueDate());
            assignment.setMaxScore(details.getMaxScore());
            return assignmentRepository.save(assignment);
        });
    }

    public boolean deleteAssignment(Long id, Long userId, String role) {
        return assignmentRepository.findById(id).map(assignment -> {
            accessControlService.getOwnedAssignment(id, userId, role);
            assignmentRepository.delete(assignment);
            return true;
        }).orElse(false);
    }

    public AssignmentSubmission submitAssignment(Long assignmentId, Long userId, String submissionText, String fileUrl) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Assignment not found"));

        AssignmentSubmission submission = submissionRepository.findByAssignmentIdAndUserId(assignmentId, userId)
                .orElse(new AssignmentSubmission());

        submission.setAssignment(assignment);
        submission.setUserId(userId);
        submission.setSubmissionText(submissionText);
        submission.setFileUrl(fileUrl);
        submission.setStatus("SUBMITTED");

        return submissionRepository.save(submission);
    }

    public Optional<AssignmentSubmission> getMySubmission(Long assignmentId, Long userId) {
        return submissionRepository.findByAssignmentIdAndUserId(assignmentId, userId);
    }

    public List<AssignmentSubmission> getSubmissionsByAssignment(Long assignmentId) {
        return submissionRepository.findByAssignmentId(assignmentId);
    }

    public Optional<AssignmentSubmission> gradeSubmission(Long subId, Integer score, String feedback) {
        return submissionRepository.findById(subId).map(sub -> {
            sub.setScore(score);
            sub.setFeedback(feedback);
            sub.setStatus("GRADED");
            AssignmentSubmission saved = submissionRepository.save(sub);
            publishAssignmentGradedEvent(saved);
            return saved;
        });
    }

    private void publishAssignmentEvent(Assignment assignment, Course course) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "ASSIGNMENT_CREATED");
            event.put("courseId", course.getId());
            event.put("courseTitle", course.getTitle());
            event.put("assignmentId", assignment.getId());
            event.put("assignmentTitle", assignment.getTitle());
            event.put("dueDate", assignment.getDueDate() != null ? assignment.getDueDate().toString() : null);
            kafkaTemplate.send("course-events", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            System.err.println("Error publishing assignment event: " + e.getMessage());
        }
    }

    private void publishAssignmentGradedEvent(AssignmentSubmission submission) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "ASSIGNMENT_GRADED");
            event.put("courseId", submission.getAssignment().getCourseId());
            event.put("assignmentId", submission.getAssignment().getId());
            event.put("assignmentTitle", submission.getAssignment().getTitle());
            event.put("submissionId", submission.getId());
            event.put("userId", submission.getUserId());
            event.put("score", submission.getScore());
            event.put("maxScore", submission.getAssignment().getMaxScore());
            event.put("feedback", submission.getFeedback());
            kafkaTemplate.send("course-events", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            System.err.println("Error publishing assignment grade event: " + e.getMessage());
        }
    }
}
