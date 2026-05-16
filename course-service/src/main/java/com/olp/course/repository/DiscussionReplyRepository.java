package com.olp.course.repository;

import com.olp.course.model.DiscussionReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscussionReplyRepository extends JpaRepository<DiscussionReply, Long> {
    java.util.List<DiscussionReply> findByThreadIdOrderByIsAcceptedDescUpvotesDescCreatedAtAsc(Long threadId);
}
