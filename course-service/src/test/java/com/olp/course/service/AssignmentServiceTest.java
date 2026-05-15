package com.olp.course.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.course.model.Assignment;
import com.olp.course.model.AssignmentSubmission;
import com.olp.course.model.Course;
import com.olp.course.repository.AssignmentRepository;
import com.olp.course.repository.AssignmentSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AssignmentSubmissionRepository submissionRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private AssignmentService assignmentService;

    @BeforeEach
    void setUp() {
        assignmentService = new AssignmentService(
                assignmentRepository,
                submissionRepository,
                accessControlService,
                kafkaTemplate,
                new ObjectMapper()
        );
    }

    @Test
    void createAssignmentChecksOwnershipAndPublishesEvent() {
        Course course = new Course();
        course.setId(8L);
        course.setTitle("Spring Boot Mastery");

        Assignment assignment = new Assignment();
        assignment.setTitle("Module Project");

        Assignment saved = new Assignment();
        saved.setId(55L);
        saved.setCourseId(8L);
        saved.setTitle("Module Project");

        when(accessControlService.getOwnedCourse(8L, 3L, "INSTRUCTOR")).thenReturn(course);
        when(assignmentRepository.save(any(Assignment.class))).thenReturn(saved);

        Assignment result = assignmentService.createAssignment(8L, assignment, 3L, "INSTRUCTOR");

        assertEquals(8L, result.getCourseId());
        verify(accessControlService).getOwnedCourse(8L, 3L, "INSTRUCTOR");
        verify(kafkaTemplate).send(eq("course-events"), contains("\"eventType\":\"ASSIGNMENT_CREATED\""));
        verify(kafkaTemplate).send(eq("course-events"), contains("\"courseTitle\":\"Spring Boot Mastery\""));
    }

    @Test
    void gradeSubmissionPublishesAssignmentGradedEvent() {
        Assignment assignment = new Assignment();
        assignment.setId(7L);
        assignment.setCourseId(2L);
        assignment.setTitle("Final Essay");
        assignment.setMaxScore(100);

        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setId(91L);
        submission.setAssignment(assignment);
        submission.setUserId(14L);

        when(submissionRepository.findById(91L)).thenReturn(Optional.of(submission));
        when(submissionRepository.save(any(AssignmentSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssignmentSubmission result = assignmentService.gradeSubmission(91L, 88, "Well done").orElseThrow();

        assertEquals("GRADED", result.getStatus());
        assertEquals(88, result.getScore());
        verify(kafkaTemplate).send(eq("course-events"), contains("\"eventType\":\"ASSIGNMENT_GRADED\""));
    }
}
