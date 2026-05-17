package com.olp.course.service;

import com.olp.course.model.Course;
import com.olp.course.model.DiscussionReply;
import com.olp.course.model.DiscussionThread;
import com.olp.course.repository.DiscussionReplyRepository;
import com.olp.course.repository.DiscussionThreadRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscussionServiceTest {

    @Mock
    private DiscussionThreadRepository threadRepository;

    @Mock
    private DiscussionReplyRepository replyRepository;

    @Mock
    private AccessControlService accessControlService;

    private DiscussionService discussionService;

    @BeforeEach
    void setUp() {
        discussionService = new DiscussionService(threadRepository, replyRepository, accessControlService);
    }

    @Test
    void initSeedsDiscussionDataWhenRepositoryIsEmpty() {
        when(threadRepository.count()).thenReturn(0L);
        when(threadRepository.save(any(DiscussionThread.class))).thenAnswer(invocation -> {
            DiscussionThread thread = invocation.getArgument(0);
            if (thread.getId() == null) {
                thread.setId(100L + invocation.getMock().hashCode());
            }
            return thread;
        });

        discussionService.init();

        verify(threadRepository, times(2)).save(any(DiscussionThread.class));
        verify(replyRepository, times(3)).save(any(DiscussionReply.class));
    }

    @Test
    void initSkipsSeedingWhenThreadsAlreadyExist() {
        when(threadRepository.count()).thenReturn(2L);

        discussionService.init();

        verify(threadRepository, never()).save(any(DiscussionThread.class));
        verify(replyRepository, never()).save(any(DiscussionReply.class));
    }

    @Test
    void createThreadSetsDefaultsBeforeSaving() {
        DiscussionThread thread = new DiscussionThread();
        thread.setTitle("Question");
        when(threadRepository.save(any(DiscussionThread.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DiscussionThread saved = discussionService.createThread(7L, thread);

        assertEquals(7L, saved.getCourseId());
        assertEquals(Boolean.FALSE, saved.getIsPinned());
        assertEquals(Boolean.FALSE, saved.getIsClosed());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void addReplyRejectsClosedDiscussion() {
        DiscussionThread thread = new DiscussionThread();
        thread.setId(5L);
        thread.setIsClosed(true);
        when(threadRepository.findById(5L)).thenReturn(Optional.of(thread));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> discussionService.addReply(5L, new DiscussionReply())
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void addReplyReturnsNotFoundWhenThreadDoesNotExist() {
        when(threadRepository.findById(5L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> discussionService.addReply(5L, new DiscussionReply())
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteThreadAllowsAuthorToDeleteOwnThread() {
        DiscussionThread thread = new DiscussionThread();
        thread.setId(9L);
        thread.setCourseId(7L);
        thread.setUserId(3L);
        when(threadRepository.findById(9L)).thenReturn(Optional.of(thread));

        boolean deleted = discussionService.deleteThread(9L, 3L, "STUDENT");

        assertTrue(deleted);
        verify(threadRepository).delete(thread);
    }

    @Test
    void deleteThreadReturnsFalseWhenThreadDoesNotExist() {
        when(threadRepository.findById(9L)).thenReturn(Optional.empty());

        boolean deleted = discussionService.deleteThread(9L, 3L, "STUDENT");

        assertFalse(deleted);
    }

    @Test
    void pinThreadAllowsInstructorWhoOwnsCourse() {
        DiscussionThread thread = new DiscussionThread();
        thread.setId(4L);
        thread.setCourseId(12L);
        when(threadRepository.findById(4L)).thenReturn(Optional.of(thread));
        when(threadRepository.save(thread)).thenReturn(thread);
        when(accessControlService.getOwnedCourse(12L, 6L, "INSTRUCTOR")).thenReturn(new Course());

        DiscussionThread updated = discussionService.pinThread(4L, 6L, "INSTRUCTOR", true);

        assertEquals(Boolean.TRUE, updated.getIsPinned());
    }

    @Test
    void acceptReplyMarksOnlySelectedReplyAccepted() {
        DiscussionThread thread = new DiscussionThread();
        thread.setId(2L);
        thread.setCourseId(8L);
        when(threadRepository.findById(2L)).thenReturn(Optional.of(thread));
        when(accessControlService.getOwnedCourse(8L, 99L, "INSTRUCTOR")).thenReturn(new Course());

        DiscussionReply first = new DiscussionReply();
        first.setId(10L);
        first.setThread(thread);
        DiscussionReply second = new DiscussionReply();
        second.setId(11L);
        second.setThread(thread);
        when(replyRepository.findByThreadIdOrderByIsAcceptedDescUpvotesDescCreatedAtAsc(2L))
                .thenReturn(List.of(first, second));
        when(replyRepository.save(any(DiscussionReply.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DiscussionReply accepted = discussionService.acceptReply(2L, 11L, 99L, "INSTRUCTOR");

        assertSame(second, accepted);
        assertEquals(Boolean.FALSE, first.getIsAccepted());
        assertEquals(Boolean.TRUE, second.getIsAccepted());
    }

    @Test
    void upvoteReplyInitializesMissingCounter() {
        DiscussionThread thread = new DiscussionThread();
        thread.setId(6L);
        DiscussionReply reply = new DiscussionReply();
        reply.setId(12L);
        reply.setUpvotes(null);
        when(threadRepository.findById(6L)).thenReturn(Optional.of(thread));
        when(replyRepository.findById(12L)).thenReturn(Optional.of(reply));
        when(replyRepository.save(reply)).thenReturn(reply);

        DiscussionReply updated = discussionService.upvoteReply(6L, 12L);

        assertEquals(1, updated.getUpvotes());
    }
}
