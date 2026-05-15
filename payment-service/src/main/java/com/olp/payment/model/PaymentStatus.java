package com.olp.payment.model;

public enum PaymentStatus {
  PENDING,
  PROCESSING,
  FULFILLMENT_PENDING,
  SUCCESS,
  FAILED,
  REFUND_PENDING,
  REFUNDED,
  CANCELLED,
}
