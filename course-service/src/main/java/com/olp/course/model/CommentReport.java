package com.olp.course.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "comment_reports")
public class CommentReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long courseId;
    private Long threadId;
    private Long replyId;
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    private ReportCategory category;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Enumerated(EnumType.STRING)
    private ReportStatus status = ReportStatus.OPEN;

    private Long reviewedBy;
    @Column(columnDefinition = "TEXT")
    private String reviewComment;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = ReportStatus.OPEN;
        if (category == null) category = ReportCategory.OTHER;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getThreadId() { return threadId; }
    public void setThreadId(Long threadId) { this.threadId = threadId; }
    public Long getReplyId() { return replyId; }
    public void setReplyId(Long replyId) { this.replyId = replyId; }
    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long reporterId) { this.reporterId = reporterId; }
    public ReportCategory getCategory() { return category; }
    public void setCategory(ReportCategory category) { this.category = category; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus status) { this.status = status; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
