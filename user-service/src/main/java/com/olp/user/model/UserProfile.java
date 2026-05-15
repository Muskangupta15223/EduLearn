package com.olp.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

  @Id
  private Long id;

  private String fullName;
  private String email;
  private String role;
  
  @Column(columnDefinition = "TEXT")
  private String bio;
  
  private String avatarUrl;
  private String expertiseAreas;
  private String mobile;
  private String accountStatus;
  private String instructorVerificationStatus;
  private String governmentIdFileUrl;
  private String governmentIdFileName;
  private String verificationComment;
  private Long verificationReviewedBy;
  private LocalDateTime verificationSubmittedAt;
  private LocalDateTime verificationReviewedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  @PrePersist
  public void onCreate() {
    if (accountStatus == null || accountStatus.isBlank()) {
      accountStatus = "ACTIVE";
    }
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
    if (updatedAt == null) {
      updatedAt = LocalDateTime.now();
    }
  }

  @PreUpdate
  public void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public String getBio() {
    return bio;
  }

  public void setBio(String bio) {
    this.bio = bio;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  public String getExpertiseAreas() {
    return expertiseAreas;
  }

  public void setExpertiseAreas(String expertiseAreas) {
    this.expertiseAreas = expertiseAreas;
  }

  public String getMobile() {
    return mobile;
  }

  public void setMobile(String mobile) {
    this.mobile = mobile;
  }

  public String getAccountStatus() {
    return accountStatus;
  }

  public void setAccountStatus(String accountStatus) {
    this.accountStatus = accountStatus;
  }

  public String getInstructorVerificationStatus() {
    return instructorVerificationStatus;
  }

  public void setInstructorVerificationStatus(String instructorVerificationStatus) {
    this.instructorVerificationStatus = instructorVerificationStatus;
  }

  public String getGovernmentIdFileUrl() {
    return governmentIdFileUrl;
  }

  public void setGovernmentIdFileUrl(String governmentIdFileUrl) {
    this.governmentIdFileUrl = governmentIdFileUrl;
  }

  public String getGovernmentIdFileName() {
    return governmentIdFileName;
  }

  public void setGovernmentIdFileName(String governmentIdFileName) {
    this.governmentIdFileName = governmentIdFileName;
  }

  public String getVerificationComment() {
    return verificationComment;
  }

  public void setVerificationComment(String verificationComment) {
    this.verificationComment = verificationComment;
  }

  public Long getVerificationReviewedBy() {
    return verificationReviewedBy;
  }

  public void setVerificationReviewedBy(Long verificationReviewedBy) {
    this.verificationReviewedBy = verificationReviewedBy;
  }

  public LocalDateTime getVerificationSubmittedAt() {
    return verificationSubmittedAt;
  }

  public void setVerificationSubmittedAt(LocalDateTime verificationSubmittedAt) {
    this.verificationSubmittedAt = verificationSubmittedAt;
  }

  public LocalDateTime getVerificationReviewedAt() {
    return verificationReviewedAt;
  }

  public void setVerificationReviewedAt(LocalDateTime verificationReviewedAt) {
    this.verificationReviewedAt = verificationReviewedAt;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
