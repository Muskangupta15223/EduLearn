package com.olp.payment.dto;

import com.olp.payment.model.PaymentMode;
import com.olp.payment.model.SubscriptionPlan;
import jakarta.validation.constraints.NotNull;

public class CreateSubscriptionRequest {

  @NotNull
  private SubscriptionPlan plan;

  private boolean autoRenew;

  private PaymentMode paymentMode;

  public SubscriptionPlan getPlan() {
    return plan;
  }

  public void setPlan(SubscriptionPlan plan) {
    this.plan = plan;
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
}
