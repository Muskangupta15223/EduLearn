package com.olp.payment.dto;

import com.olp.payment.model.PaymentMode;
import com.olp.payment.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentResponse {

  private Long id;
  private Long enrollmentId;
  private BigDecimal amount;
  private String currency;
  private PaymentMode paymentMode;
  private PaymentStatus status;
  private String gatewayTransactionId;
  private String razorpayPaymentId;
  private String idempotencyKey;
  private String failureReason;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private String planReference;
  private String providerRefundId;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getEnrollmentId() {
    return enrollmentId;
  }

  public void setEnrollmentId(Long enrollmentId) {
    this.enrollmentId = enrollmentId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public PaymentMode getPaymentMode() {
    return paymentMode;
  }

  public void setPaymentMode(PaymentMode paymentMode) {
    this.paymentMode = paymentMode;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public void setStatus(PaymentStatus status) {
    this.status = status;
  }

  public String getGatewayTransactionId() {
    return gatewayTransactionId;
  }

  public void setGatewayTransactionId(String gatewayTransactionId) {
    this.gatewayTransactionId = gatewayTransactionId;
  }

  public String getRazorpayPaymentId() {
    return razorpayPaymentId;
  }

  public void setRazorpayPaymentId(String razorpayPaymentId) {
    this.razorpayPaymentId = razorpayPaymentId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
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

  public String getPlanReference() {
    return planReference;
  }

  public void setPlanReference(String planReference) {
    this.planReference = planReference;
  }

  public String getProviderRefundId() {
    return providerRefundId;
  }

  public void setProviderRefundId(String providerRefundId) {
    this.providerRefundId = providerRefundId;
  }
}
