package com.olp.course.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "courses")
public class Course {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String title;
  
  @Column(columnDefinition = "TEXT")
  private String description;
  
  private BigDecimal price;

  private Long instructorId;
  private String instructorName;
  private String category;
  private String level;
  private String language;
  private String status; // DRAFT, PENDING, PUBLISHED
  private String reviewStatus;
  @Column(columnDefinition = "TEXT")
  private String reviewComment;
  private Long reviewedBy;
  private LocalDateTime reviewedAt;
  private LocalDateTime submittedForReviewAt;
  private String thumbnail;
  private Double rating;
  private Integer studentsCount;
  private String duration;
  @Transient
  private Boolean instructorVerified;
  @Transient
  private String instructorVerificationStatus;

  @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Module> modules = new ArrayList<>();

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
    if (status == null) status = "DRAFT";
    if (reviewStatus == null) reviewStatus = "DRAFT";
    if (rating == null) rating = 0.0;
    if (studentsCount == null) studentsCount = 0;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  // Getters and Setters
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public BigDecimal getPrice() { return price; }
  public void setPrice(BigDecimal price) { this.price = price; }
  public Long getInstructorId() { return instructorId; }
  public void setInstructorId(Long instructorId) { this.instructorId = instructorId; }
  public String getInstructorName() { return instructorName; }
  public void setInstructorName(String instructorName) { this.instructorName = instructorName; }
  public String getCategory() { return category; }
  public void setCategory(String category) { this.category = category; }
  public String getLevel() { return level; }
  public void setLevel(String level) { this.level = level; }
  public String getLanguage() { return language; }
  public void setLanguage(String language) { this.language = language; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getReviewStatus() { return reviewStatus; }
  public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
  public String getReviewComment() { return reviewComment; }
  public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
  public Long getReviewedBy() { return reviewedBy; }
  public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
  public LocalDateTime getReviewedAt() { return reviewedAt; }
  public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
  public LocalDateTime getSubmittedForReviewAt() { return submittedForReviewAt; }
  public void setSubmittedForReviewAt(LocalDateTime submittedForReviewAt) { this.submittedForReviewAt = submittedForReviewAt; }
  public String getThumbnail() { return thumbnail; }
  public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }
  public Double getRating() { return rating; }
  public void setRating(Double rating) { this.rating = rating; }
  public Integer getStudentsCount() { return studentsCount; }
  public void setStudentsCount(Integer studentsCount) { this.studentsCount = studentsCount; }
  public String getDuration() { return duration; }
  public void setDuration(String duration) { this.duration = duration; }
  public Boolean getInstructorVerified() { return instructorVerified; }
  public void setInstructorVerified(Boolean instructorVerified) { this.instructorVerified = instructorVerified; }
  public String getInstructorVerificationStatus() { return instructorVerificationStatus; }
  public void setInstructorVerificationStatus(String instructorVerificationStatus) { this.instructorVerificationStatus = instructorVerificationStatus; }
  public List<Module> getModules() { return modules; }
  public void setModules(List<Module> modules) { this.modules = modules; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
