package com.olp.enrollment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "enrollments",
    uniqueConstraints = @UniqueConstraint(name = "uk_user_course_enrollment", columnNames = {"userId", "courseId"})
)
public class Enrollment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long userId;
  private Long courseId;
  private String status;
  private Integer progress;
  private String lastLesson;
  private LocalDateTime completedAt;
  @Column(nullable = false)
  private Boolean certificateIssued = Boolean.FALSE;
  private LocalDateTime enrolledAt;

  @PrePersist
  public void onCreate() {
    if (progress == null) {
      progress = 0;
    }
    if (status == null || status.isBlank()) {
      status = "PENDING_PAYMENT";
    }
    if (certificateIssued == null) {
      certificateIssued = Boolean.FALSE;
    }
    if (enrolledAt == null) {
      enrolledAt = LocalDateTime.now();
    }
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public Long getCourseId() {
    return courseId;
  }

  public void setCourseId(Long courseId) {
    this.courseId = courseId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public LocalDateTime getEnrolledAt() {
    return enrolledAt;
  }

  public void setEnrolledAt(LocalDateTime enrolledAt) {
    this.enrolledAt = enrolledAt;
  }

  public Integer getProgress() { return progress; }
  public void setProgress(Integer progress) { this.progress = progress; }
  public String getLastLesson() { return lastLesson; }
  public void setLastLesson(String lastLesson) { this.lastLesson = lastLesson; }
  public LocalDateTime getCompletedAt() { return completedAt; }
  public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
  public Boolean getCertificateIssued() { return certificateIssued; }
  public void setCertificateIssued(Boolean certificateIssued) { this.certificateIssued = certificateIssued; }
}
