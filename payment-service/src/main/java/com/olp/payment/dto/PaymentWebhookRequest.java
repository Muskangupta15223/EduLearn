package com.olp.payment.dto;

public class PaymentWebhookRequest {

  private String gatewayTransactionId;
  private String gatewayPaymentId;
  private String providerEventId;
  private String status;
  private String failureReason;

  public String getGatewayTransactionId() {
    return gatewayTransactionId;
  }

  public void setGatewayTransactionId(String gatewayTransactionId) {
    this.gatewayTransactionId = gatewayTransactionId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getGatewayPaymentId() {
    return gatewayPaymentId;
  }

  public void setGatewayPaymentId(String gatewayPaymentId) {
    this.gatewayPaymentId = gatewayPaymentId;
  }

  public String getProviderEventId() {
    return providerEventId;
  }

  public void setProviderEventId(String providerEventId) {
    this.providerEventId = providerEventId;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }
}
