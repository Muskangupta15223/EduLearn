package com.olp.payment.dto;

import com.olp.payment.model.PaymentMode;
import com.olp.payment.model.SubscriptionPlan;
import com.olp.payment.model.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class SubscriptionResponse {

  private Long id;
  private Long userId;
  private SubscriptionPlan plan;
  private LocalDate startDate;
  private LocalDate endDate;
  private SubscriptionStatus status;
  private BigDecimal amountPaid;
  private boolean autoRenew;
  private PaymentMode paymentMode;
  private Long paymentTransactionId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

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

  public SubscriptionPlan getPlan() {
    return plan;
  }

  public void setPlan(SubscriptionPlan plan) {
    this.plan = plan;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public SubscriptionStatus getStatus() {
    return status;
  }

  public void setStatus(SubscriptionStatus status) {
    this.status = status;
  }

  public BigDecimal getAmountPaid() {
    return amountPaid;
  }

  public void setAmountPaid(BigDecimal amountPaid) {
    this.amountPaid = amountPaid;
  }

  public boolean isAutoRenew() {
    return autoRenew;
  }

  public void setAutoRenew(boolean autoRenew) {
    this.autoRenew = autoRenew;
  }

  public PaymentMode getPaymentMode() {
    return paymentMode;
  }

  public void setPaymentMode(PaymentMode paymentMode) {
    this.paymentMode = paymentMode;
  }

  public Long getPaymentTransactionId() {
    return paymentTransactionId;
  }

  public void setPaymentTransactionId(Long paymentTransactionId) {
    this.paymentTransactionId = paymentTransactionId;
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
