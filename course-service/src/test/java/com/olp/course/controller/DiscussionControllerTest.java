package com.olp.course.controller;

import com.olp.course.constant.CourseConstants;
import com.olp.course.model.DiscussionReply;
import com.olp.course.model.DiscussionThread;
import com.olp.course.service.DiscussionService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscussionControllerTest {

    @Mock
    private DiscussionService discussionService;

    @Mock
    private RestTemplate restTemplate;

    private DiscussionController discussionController;

    @BeforeEach
    void setUp() {
        discussionController = new DiscussionController(discussionService, restTemplate);
    }

    @Test
    void createThreadRejectsMissingUserId() {
        ResponseEntity<?> response = discussionController.createThread(5L, new DiscussionThread(), null, "STUDENT");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(
                Map.of(CourseConstants.KEY_ERROR, CourseConstants.MSG_MISSING_USER_ID),
                response.getBody()
        );
    }

    @Test
    void createThreadDefaultsUserNameWhenEnrollmentCheckPasses() {
        DiscussionThread thread = new DiscussionThread();
        thread.setTitle("Need help");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(DiscussionController.EnrollmentAccessResponse.class)))
                .thenReturn(ResponseEntity.ok(new DiscussionController.EnrollmentAccessResponse(true)));
        when(discussionService.createThread(5L, thread)).thenReturn(thread);

        ResponseEntity<?> response = discussionController.createThread(5L, thread, 9L, "STUDENT");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(9L, thread.getUserId());
        assertEquals("Student", thread.getUserName());
        assertSame(thread, response.getBody());
    }

    @Test
    void getThreadsAllowsInstructorWithoutEnrollmentLookup() {
        List<DiscussionThread> threads = List.of(new DiscussionThread());
        when(discussionService.getThreadsByCourse(3L)).thenReturn(threads);

        ResponseEntity<?> response = discussionController.getThreads(3L, null, "INSTRUCTOR");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(threads, response.getBody());
        verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(DiscussionController.EnrollmentAccessResponse.class));
    }

    @Test
    void getThreadReturnsForbiddenWhenEnrollmentCheckFails() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(DiscussionController.EnrollmentAccessResponse.class)))
                .thenThrow(new IllegalStateException("downstream unavailable"));

        ResponseEntity<?> response = discussionController.getThread(3L, 8L, 22L, "STUDENT");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(
                Map.of(CourseConstants.KEY_ERROR, CourseConstants.MSG_MUST_BE_ENROLLED_TO_VIEW),
                response.getBody()
        );
    }

    @Test
    void getThreadReturnsPayloadWhenDiscussionExists() {
        DiscussionThread thread = new DiscussionThread();
        thread.setId(8L);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(DiscussionController.EnrollmentAccessResponse.class)))
                .thenReturn(ResponseEntity.ok(new DiscussionController.EnrollmentAccessResponse(true)));
        when(discussionService.getThreadById(8L)).thenReturn(Optional.of(thread));

        ResponseEntity<?> response = discussionController.getThread(3L, 8L, 22L, "STUDENT");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(thread, response.getBody());
    }

    @Test
    void addReplyDefaultsUserNameWhenReplyIsCreated() {
        DiscussionReply reply = new DiscussionReply();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(DiscussionController.EnrollmentAccessResponse.class)))
                .thenReturn(ResponseEntity.ok(new DiscussionController.EnrollmentAccessResponse(true)));
        when(discussionService.addReply(10L, reply)).thenReturn(reply);

        ResponseEntity<?> response = discussionController.addReply(3L, 10L, reply, 12L, "STUDENT");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(12L, reply.getUserId());
        assertEquals("Student", reply.getUserName());
    }

    @Test
    void upvoteReplyRejectsUnenrolledUser() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(DiscussionController.EnrollmentAccessResponse.class)))
                .thenReturn(ResponseEntity.ok(new DiscussionController.EnrollmentAccessResponse(false)));

        ResponseEntity<?> response = discussionController.upvoteReply(3L, 10L, 77L, 12L, "STUDENT");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(
                Map.of(CourseConstants.KEY_ERROR, CourseConstants.MSG_MUST_BE_ENROLLED_TO_VOTE),
                response.getBody()
        );
    }

    @Test
    void upvoteReplyReturnsUpdatedReplyForEnrolledUser() {
        DiscussionReply reply = new DiscussionReply();
        reply.setId(77L);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(DiscussionController.EnrollmentAccessResponse.class)))
                .thenReturn(ResponseEntity.ok(new DiscussionController.EnrollmentAccessResponse(true)));
        when(discussionService.upvoteReply(10L, 77L)).thenReturn(reply);

        ResponseEntity<?> response = discussionController.upvoteReply(3L, 10L, 77L, 12L, "STUDENT");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(reply, response.getBody());
    }
}
