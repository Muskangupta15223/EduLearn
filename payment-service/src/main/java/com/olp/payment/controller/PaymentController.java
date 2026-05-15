package com.olp.payment.controller;

import com.olp.payment.dto.CreatePaymentRequest;
import com.olp.payment.dto.CreateSubscriptionRequest;
import com.olp.payment.dto.PaymentResponse;
import com.olp.payment.dto.PaymentWebhookRequest;
import com.olp.payment.dto.SubscriptionResponse;
import com.olp.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/payments")
public class PaymentController {

  private final PaymentService paymentService;

  public PaymentController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @PostMapping
  public ResponseEntity<PaymentResponse> createPayment(
    @Valid @RequestBody CreatePaymentRequest request,
    @RequestHeader(
      value = "Idempotency-Key",
      required = false
    ) String idempotencyKey
  ) {
    return ResponseEntity.ok(
      paymentService.createPayment(request, idempotencyKey)
    );
  }

  @GetMapping("/{id}")
  public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long id) {
    return ResponseEntity.ok(paymentService.getPayment(id));
  }

  @GetMapping("/{id}/receipt")
  public ResponseEntity<byte[]> downloadReceipt(@PathVariable Long id) {
    PaymentResponse payment = paymentService.getPayment(id);
    String content = """
        EduLearn Payment Receipt
        Payment ID: %s
        Enrollment ID: %s
        Amount: %s %s
        Status: %s
        Gateway Reference: %s
        Created At: %s
        """.formatted(
            payment.getId(),
            payment.getEnrollmentId(),
            payment.getCurrency(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getRazorpayPaymentId() != null ? payment.getRazorpayPaymentId() : payment.getGatewayTransactionId(),
            payment.getCreatedAt()
        );

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payment-receipt-" + id + ".txt\"")
        .contentType(MediaType.TEXT_PLAIN)
        .body(content.getBytes(StandardCharsets.UTF_8));
  }

  @GetMapping("/all")
  public ResponseEntity<List<PaymentResponse>> getAllPayments(
          @RequestHeader(value = "X-User-Role", required = false) String role) {
      return ResponseEntity.ok(paymentService.getAllPayments(role));
  }

  @GetMapping("/me")
  public ResponseEntity<java.util.List<PaymentResponse>> getMyPayments(
          @RequestHeader(value = "X-User-Id", required = false) Long userId) {
      return ResponseEntity.ok(paymentService.getPaymentsByUser(userId));
  }

  @PostMapping("/webhook")
  public ResponseEntity<PaymentResponse> processWebhook(
    @RequestBody PaymentWebhookRequest request
  ) {
    return ResponseEntity.ok(paymentService.processWebhook(request));
  }

  @PostMapping("/razorpay/webhook")
  public ResponseEntity<PaymentResponse> processRazorpayWebhook(
    @RequestBody String payload,
    @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature
  ) {
    return ResponseEntity.ok(paymentService.processRazorpayWebhook(payload, signature));
  }

  @PostMapping("/razorpay/order")
  public ResponseEntity<com.olp.payment.dto.RazorpayOrderResponse> createRazorpayOrder(
          @RequestBody com.olp.payment.dto.RazorpayOrderRequest request,
          @RequestHeader(value = "X-User-Id", required = false) Long userId,
          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
      return ResponseEntity.ok(paymentService.createRazorpayOrder(request, userId, idempotencyKey));
  }

  @PostMapping("/razorpay/verify")
  public ResponseEntity<PaymentResponse> verifyRazorpay(
          @RequestBody com.olp.payment.dto.RazorpayVerifyRequest request,
          @RequestHeader(value = "X-User-Id", required = false) Long userId) {
      return ResponseEntity.ok(paymentService.verifyRazorpaySignature(request, userId));
  }

  @PostMapping("/{id}/refund")
  public ResponseEntity<PaymentResponse> refundPayment(
          @PathVariable Long id,
          @RequestHeader(value = "X-User-Id", required = false) Long userId,
          @RequestHeader(value = "X-User-Role", required = false) String role) {
      return ResponseEntity.ok(paymentService.refundPayment(id, userId, role));
  }

  @PostMapping("/subscriptions")
  public ResponseEntity<SubscriptionResponse> subscribe(
          @Valid @RequestBody CreateSubscriptionRequest request,
          @RequestHeader(value = "X-User-Id", required = false) Long userId,
          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
      return ResponseEntity.ok(paymentService.subscribe(request, userId, idempotencyKey));
  }

  @GetMapping("/subscriptions/me")
  public ResponseEntity<SubscriptionResponse> getMySubscription(
          @RequestHeader(value = "X-User-Id", required = false) Long userId) {
      return ResponseEntity.ok(paymentService.getMySubscription(userId));
  }

  @GetMapping("/subscriptions/history")
  public ResponseEntity<List<SubscriptionResponse>> getSubscriptionHistory(
          @RequestHeader(value = "X-User-Id", required = false) Long userId) {
      return ResponseEntity.ok(paymentService.getSubscriptionsByUser(userId));
  }

  @GetMapping("/subscriptions")
  public ResponseEntity<List<SubscriptionResponse>> getAllActiveSubscriptions(
          @RequestHeader(value = "X-User-Role", required = false) String role) {
      return ResponseEntity.ok(paymentService.getAllActiveSubscriptions(role));
  }

  @PostMapping("/subscriptions/{id}/cancel")
  public ResponseEntity<SubscriptionResponse> cancelSubscription(
          @PathVariable Long id,
          @RequestHeader(value = "X-User-Id", required = false) Long userId,
          @RequestHeader(value = "X-User-Role", required = false) String role) {
      return ResponseEntity.ok(paymentService.cancelSubscription(id, userId, role));
  }
}
