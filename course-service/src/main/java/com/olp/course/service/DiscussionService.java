package com.olp.course.service;

import com.olp.course.model.DiscussionReply;
import com.olp.course.model.DiscussionThread;
import com.olp.course.model.Course;
import com.olp.course.repository.DiscussionReplyRepository;
import com.olp.course.repository.DiscussionThreadRepository;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class DiscussionService {

    private final DiscussionThreadRepository threadRepository;
    private final DiscussionReplyRepository replyRepository;
    private final AccessControlService accessControlService;

    public DiscussionService(DiscussionThreadRepository threadRepository,
                             DiscussionReplyRepository replyRepository,
                             AccessControlService accessControlService) {
        this.threadRepository = threadRepository;
        this.replyRepository = replyRepository;
        this.accessControlService = accessControlService;
    }

    @PostConstruct
    public void init() {
        if (threadRepository.count() == 0) {
            // Assume courseId 1 exists (e.g. from dummy data in course table)
            Long dummyCourseId = 1L;

            DiscussionThread thread1 = new DiscussionThread();
            thread1.setCourseId(dummyCourseId);
            thread1.setTitle("Welcome to the course! Introduce yourselves.");
            thread1.setContent("Hi everyone, please use this thread to introduce yourselves, share your background, and tell us what you hope to learn from this course.");
            thread1.setUserId(1L);
            thread1.setUserName("Instructor John");
            thread1 = threadRepository.save(thread1);

            DiscussionReply reply1 = new DiscussionReply();
            reply1.setThread(thread1);
            reply1.setContent("Hello! I'm Alice. I'm a software developer looking to improve my skills.");
            reply1.setUserId(2L);
            reply1.setUserName("Alice");
            replyRepository.save(reply1);

            DiscussionReply reply2 = new DiscussionReply();
            reply2.setThread(thread1);
            reply2.setContent("Hi Alice, nice to meet you! I'm Bob, currently a student.");
            reply2.setUserId(3L);
            reply2.setUserName("Bob");
            replyRepository.save(reply2);

            DiscussionThread thread2 = new DiscussionThread();
            thread2.setCourseId(dummyCourseId);
            thread2.setTitle("Question about Module 1 Assignment");
            thread2.setContent("I'm having trouble understanding the requirements for the second part of the assignment. Could anyone clarify?");
            thread2.setUserId(2L);
            thread2.setUserName("Alice");
            thread2 = threadRepository.save(thread2);

            DiscussionReply reply3 = new DiscussionReply();
            reply3.setThread(thread2);
            reply3.setContent("Sure, the second part asks you to implement the interface provided in the starter code. Let me know if you need more help.");
            reply3.setUserId(1L);
            reply3.setUserName("Instructor John");
            replyRepository.save(reply3);
        }
    }

    public DiscussionThread createThread(Long courseId, DiscussionThread thread) {
        thread.setCourseId(courseId);
        if (thread.getIsPinned() == null) {
            thread.setIsPinned(false);
        }
        if (thread.getIsClosed() == null) {
            thread.setIsClosed(false);
        }
        if (thread.getCreatedAt() == null) {
            thread.setCreatedAt(LocalDateTime.now());
        }
        return threadRepository.save(thread);
    }

    public List<DiscussionThread> getThreadsByCourse(Long courseId) {
        return threadRepository.findByCourseIdOrderByIsPinnedDescCreatedAtDesc(courseId);
    }

    public Optional<DiscussionThread> getThreadById(Long threadId) {
        return threadRepository.findById(threadId);
    }

    public boolean deleteThread(Long threadId, Long userId, String role) {
        return threadRepository.findById(threadId).map(thread -> {
            requireAuthorAdminOrModerator(thread, userId, role);
            threadRepository.delete(thread);
            return true;
        }).orElse(false);
    }

    public DiscussionReply addReply(Long threadId, DiscussionReply reply) {
        DiscussionThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new RuntimeException("Thread not found"));
        if (Boolean.TRUE.equals(thread.getIsClosed())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This discussion is closed");
        }
        reply.setThread(thread);
        if (reply.getCreatedAt() == null) {
            reply.setCreatedAt(LocalDateTime.now());
        }
        return replyRepository.save(reply);
    }

    public boolean deleteReply(Long replyId, Long userId, String role) {
        return replyRepository.findById(replyId).map(reply -> {
            requireAuthorAdminOrModerator(reply.getThread(), userId, role, reply.getUserId());
            replyRepository.delete(reply);
            return true;
        }).orElse(false);
    }

    public DiscussionThread pinThread(Long threadId, Long userId, String role, boolean pinned) {
        return threadRepository.findById(threadId)
                .map(thread -> {
                    requireModerator(thread, userId, role);
                    thread.setIsPinned(pinned);
                    return threadRepository.save(thread);
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thread not found"));
    }

    public DiscussionThread closeThread(Long threadId, Long userId, String role, boolean closed) {
        return threadRepository.findById(threadId)
                .map(thread -> {
                    requireModerator(thread, userId, role);
                    thread.setIsClosed(closed);
                    return threadRepository.save(thread);
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thread not found"));
    }

    public DiscussionReply acceptReply(Long threadId, Long replyId, Long userId, String role) {
        DiscussionThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thread not found"));
        requireModerator(thread, userId, role);

        List<DiscussionReply> replies = replyRepository.findByThreadIdOrderByIsAcceptedDescUpvotesDescCreatedAtAsc(threadId);
        DiscussionReply acceptedReply = null;
        for (DiscussionReply reply : replies) {
            boolean accepted = reply.getId().equals(replyId);
            reply.setIsAccepted(accepted);
            DiscussionReply saved = replyRepository.save(reply);
            if (accepted) {
                acceptedReply = saved;
            }
        }
        if (acceptedReply == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reply not found");
        }
        return acceptedReply;
    }

    public DiscussionReply upvoteReply(Long threadId, Long replyId) {
        threadRepository.findById(threadId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thread not found"));
        return replyRepository.findById(replyId)
                .map(reply -> {
                    if (reply.getUpvotes() == null) {
                        reply.setUpvotes(0);
                    }
                    reply.setUpvotes(reply.getUpvotes() + 1);
                    return replyRepository.save(reply);
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reply not found"));
    }

    private void requireAuthorAdminOrModerator(DiscussionThread thread, Long actorUserId, String actorRole) {
        requireAuthorAdminOrModerator(thread, actorUserId, actorRole, thread.getUserId());
    }

    private void requireAuthorAdminOrModerator(DiscussionThread thread, Long actorUserId, String actorRole, Long ownerUserId) {
        if ("ADMIN".equalsIgnoreCase(actorRole)) {
            return;
        }
        if (actorUserId != null && ownerUserId != null && ownerUserId.equals(actorUserId)) {
            return;
        }
        if ("INSTRUCTOR".equalsIgnoreCase(actorRole)) {
            Course course = accessControlService.getOwnedCourse(thread.getCourseId(), actorUserId, actorRole);
            if (course != null) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to modify this discussion");
    }

    private void requireModerator(DiscussionThread thread, Long actorUserId, String actorRole) {
        if ("ADMIN".equalsIgnoreCase(actorRole)) {
            return;
        }
        accessControlService.getOwnedCourse(thread.getCourseId(), actorUserId, actorRole);
    }
}
