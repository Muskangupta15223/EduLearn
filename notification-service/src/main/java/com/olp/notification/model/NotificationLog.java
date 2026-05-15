package com.olp.notification.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_logs")
public class NotificationLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long userId;
  private String recipient;
  private String title;
  private String message;
  private String type; // enrollment, course, etc.
  private Boolean isRead = false;
  private LocalDateTime sentAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getRecipient() {
    return recipient;
  }

  public void setRecipient(String recipient) {
    this.recipient = recipient;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public LocalDateTime getSentAt() {
    return sentAt;
  }

  public void setSentAt(LocalDateTime sentAt) {
    this.sentAt = sentAt;
  }

  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getType() { return type; }
  public void setType(String type) { this.type = type; }
  public Boolean getIsRead() { return isRead; }
  public void setIsRead(Boolean isRead) { this.isRead = isRead; }
}
