package com.olp.course.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "discussion_replies")
public class DiscussionReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "thread_id", nullable = false)
    @JsonIgnore
    private DiscussionThread thread;

    private Long userId;
    private String userName;

    @Column(columnDefinition = "TEXT")
    private String content;

    private Boolean isAccepted = false;
    private Integer upvotes = 0;
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public DiscussionThread getThread() { return thread; }
    public void setThread(DiscussionThread thread) { this.thread = thread; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Boolean getIsAccepted() { return isAccepted; }
    public void setIsAccepted(Boolean isAccepted) { this.isAccepted = isAccepted; }
    public Integer getUpvotes() { return upvotes; }
    public void setUpvotes(Integer upvotes) { this.upvotes = upvotes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
