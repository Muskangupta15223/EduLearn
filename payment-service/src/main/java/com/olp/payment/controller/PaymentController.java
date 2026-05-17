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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;

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
    byte[] receipt = generateReceiptImage(payment);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payment-receipt-" + id + ".png\"")
        .contentType(MediaType.IMAGE_PNG)
        .body(receipt);
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

  private byte[] generateReceiptImage(PaymentResponse payment) {
    try {
      int width = 1200;
      int height = 760;
      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      g.setPaint(new GradientPaint(0, 0, new Color(241, 245, 249), 0, height, new Color(255, 255, 255)));
      g.fillRect(0, 0, width, height);

      g.setColor(new Color(255, 255, 255));
      g.fillRoundRect(70, 52, width - 140, height - 104, 28, 28);
      g.setColor(new Color(37, 99, 235));
      g.setStroke(new BasicStroke(4f));
      g.drawRoundRect(70, 52, width - 140, height - 104, 28, 28);

      g.setColor(new Color(15, 23, 42));
      g.setFont(new Font("SansSerif", Font.BOLD, 34));
      g.drawString("EduLearn Payment Receipt", 110, 120);

      g.setColor(new Color(71, 85, 105));
      g.setFont(new Font("SansSerif", Font.PLAIN, 18));
      g.drawString("Secure payment confirmation for your course purchase", 110, 155);

      g.setColor(new Color(16, 185, 129));
      g.fillRoundRect(width - 260, 92, 120, 40, 20, 20);
      g.setColor(Color.WHITE);
      g.setFont(new Font("SansSerif", Font.BOLD, 18));
      drawCentered(g, payment.getStatus() != null ? payment.getStatus().name() : "PAID", width - 200, 118);

      int left = 110;
      int top = 220;
      int rowGap = 74;
      drawLabelValue(g, "Receipt No", safeValue(payment.getId()), left, top);
      drawLabelValue(g, "Enrollment ID", safeValue(payment.getEnrollmentId()), left + 360, top);
      drawLabelValue(g, "Amount Paid", safeValue(payment.getCurrency()) + " " + safeValue(payment.getAmount()), left, top + rowGap);
      drawLabelValue(g, "Payment Date", payment.getCreatedAt() != null ? payment.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")) : "N/A", left + 360, top + rowGap);
      drawLabelValue(g, "Payment Method", payment.getPaymentMode() != null ? payment.getPaymentMode().name().replace('_', ' ') : "Razorpay", left, top + (rowGap * 2));
      drawLabelValue(g, "Reference", resolveReference(payment), left + 360, top + (rowGap * 2));
      drawLabelValue(g, "Plan", payment.getPlanReference() != null ? payment.getPlanReference() : "Course Purchase", left, top + (rowGap * 3));

      g.setColor(new Color(226, 232, 240));
      g.fillRoundRect(110, 562, width - 220, 100, 22, 22);
      g.setColor(new Color(51, 65, 85));
      g.setFont(new Font("SansSerif", Font.BOLD, 18));
      g.drawString("Thank you for learning with EduLearn.", 138, 608);
      g.setFont(new Font("SansSerif", Font.PLAIN, 16));
      g.drawString("Keep this receipt for your records. It confirms your successful payment and course access.", 138, 638);

      g.dispose();
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      ImageIO.write(image, "png", output);
      return output.toByteArray();
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to generate payment receipt image", ex);
    }
  }

  private void drawLabelValue(Graphics2D g, String label, String value, int x, int y) {
    g.setColor(new Color(100, 116, 139));
    g.setFont(new Font("SansSerif", Font.BOLD, 15));
    g.drawString(label.toUpperCase(), x, y);
    g.setColor(new Color(15, 23, 42));
    g.setFont(new Font("SansSerif", Font.BOLD, 24));
    g.drawString(value, x, y + 32);
  }

  private void drawCentered(Graphics2D g, String text, int centerX, int baselineY) {
    FontMetrics fm = g.getFontMetrics();
    g.drawString(text, centerX - (fm.stringWidth(text) / 2), baselineY);
  }

  private String resolveReference(PaymentResponse payment) {
    if (payment.getRazorpayPaymentId() != null && !payment.getRazorpayPaymentId().isBlank()) {
      return payment.getRazorpayPaymentId();
    }
    if (payment.getGatewayTransactionId() != null && !payment.getGatewayTransactionId().isBlank()) {
      return payment.getGatewayTransactionId();
    }
    return "N/A";
  }

  private String safeValue(Object value) {
    return value == null ? "N/A" : value.toString();
  }
}
