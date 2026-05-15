package com.olp.payment.dto;

import java.math.BigDecimal;

public class RazorpayOrderRequest {
    private String plan;
    private BigDecimal amount;
    private Long courseId;
    
    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
}
