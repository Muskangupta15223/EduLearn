package com.olp.enrollment.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "lesson_progress",
    uniqueConstraints = @UniqueConstraint(name = "uk_user_course_lesson", columnNames = {"userId", "courseId", "lessonId"})
)
public class LessonProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long courseId;

    @Column(nullable = false)
    private Long lessonId;

    private String lessonTitle;

    @Column(nullable = false)
    private Integer percentComplete;

    @Column(nullable = false)
    private String status;

    private LocalDateTime firstAccessedAt;
    private LocalDateTime lastAccessedAt;
    private LocalDateTime completedAt;

    @PrePersist
    public void onCreate() {
        if (percentComplete == null) {
            percentComplete = 0;
        }
        if (status == null) {
            status = "NOT_STARTED";
        }
        if (firstAccessedAt == null) {
            firstAccessedAt = LocalDateTime.now();
        }
        if (lastAccessedAt == null) {
            lastAccessedAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getLessonId() { return lessonId; }
    public void setLessonId(Long lessonId) { this.lessonId = lessonId; }
    public String getLessonTitle() { return lessonTitle; }
    public void setLessonTitle(String lessonTitle) { this.lessonTitle = lessonTitle; }
    public Integer getPercentComplete() { return percentComplete; }
    public void setPercentComplete(Integer percentComplete) { this.percentComplete = percentComplete; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getFirstAccessedAt() { return firstAccessedAt; }
    public void setFirstAccessedAt(LocalDateTime firstAccessedAt) { this.firstAccessedAt = firstAccessedAt; }
    public LocalDateTime getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(LocalDateTime lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
