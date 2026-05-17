package com.olp.course.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olp.course.dto.AssignmentDtos.AssignmentRequest;
import com.olp.course.dto.AssignmentDtos.AssignmentSubmissionRequest;
import com.olp.course.dto.AssignmentDtos.GradeSubmissionRequest;
import com.olp.course.model.Assignment;
import com.olp.course.model.AssignmentSubmission;
import com.olp.course.service.AssignmentService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AssignmentControllerTest {

    @Mock
    private AssignmentService assignmentService;

    private AssignmentController assignmentController;

    @BeforeEach
    void setUp() {
        assignmentController = new AssignmentController(assignmentService);
    }

    @Test
    void createMapsRequestAndResponse() {
        Assignment saved = new Assignment();
        saved.setId(10L);
        saved.setCourseId(5L);
        saved.setTitle("Homework");
        when(assignmentService.createAssignment(any(), any(), any(), any())).thenReturn(saved);

        ResponseEntity<?> response = assignmentController.create(
                5L,
                new AssignmentRequest("Homework", "Desc", null, 100),
                7L,
                "INSTRUCTOR"
        );

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getByIdReturnsNotFoundWhenMissing() {
        when(assignmentService.getAssignmentById(9L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = assignmentController.getById(9L);

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void deleteReturnsOkWhenServiceDeletes() {
        when(assignmentService.deleteAssignment(3L, 7L, "INSTRUCTOR")).thenReturn(true);

        ResponseEntity<Void> response = assignmentController.delete(3L, 7L, "INSTRUCTOR");

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void submitRejectsMissingUserId() {
        ResponseEntity<?> response = assignmentController.submit(
                4L,
                new AssignmentSubmissionRequest("Text", null),
                null
        );

        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void submitUsesDefaultEmptySubmissionText() {
        Assignment assignment = new Assignment();
        assignment.setId(4L);
        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setId(8L);
        submission.setAssignment(assignment);
        when(assignmentService.submitAssignment(4L, 9L, "", null)).thenReturn(submission);

        ResponseEntity<?> response = assignmentController.submit(
                4L,
                new AssignmentSubmissionRequest(null, null),
                9L
        );

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getMySubmissionReturnsNotFoundWhenMissing() {
        when(assignmentService.getMySubmission(6L, 9L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = assignmentController.getMySubmission(6L, 9L);

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void getSubmissionsMapsList() {
        Assignment assignment = new Assignment();
        assignment.setId(2L);
        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setId(3L);
        submission.setAssignment(assignment);
        when(assignmentService.getSubmissionsByAssignment(2L)).thenReturn(List.of(submission));

        ResponseEntity<?> response = assignmentController.getSubmissions(2L);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void gradeSubmissionPassesTypedBody() {
        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setId(12L);
        when(assignmentService.gradeSubmission(12L, 90, "Nice")).thenReturn(Optional.of(submission));

        ResponseEntity<?> response = assignmentController.gradeSubmission(
                12L,
                new GradeSubmissionRequest(90, "Nice")
        );

        assertEquals(200, response.getStatusCode().value());
        verify(assignmentService).gradeSubmission(12L, 90, "Nice");
    }
}
