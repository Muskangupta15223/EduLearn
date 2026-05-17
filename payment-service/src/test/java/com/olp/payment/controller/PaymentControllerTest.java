package com.olp.payment.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
// import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olp.payment.dto.CreatePaymentRequest;
import com.olp.payment.dto.CreateSubscriptionRequest;
import com.olp.payment.dto.PaymentResponse;
import com.olp.payment.dto.PaymentWebhookRequest;
import com.olp.payment.dto.RazorpayOrderRequest;
import com.olp.payment.dto.RazorpayOrderResponse;
import com.olp.payment.dto.RazorpayVerifyRequest;
import com.olp.payment.dto.SubscriptionResponse;
import com.olp.payment.model.PaymentMode;
import com.olp.payment.model.PaymentStatus;
import com.olp.payment.model.SubscriptionPlan;
import com.olp.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

  @Mock
  private PaymentService paymentService;

  private PaymentController paymentController;

  @BeforeEach
  void setUp() {
    paymentController = new PaymentController(paymentService);
  }

  @Test
  void createPaymentDelegatesToServiceWithIdempotencyKey() {
    CreatePaymentRequest request = new CreatePaymentRequest();
    PaymentResponse responseBody = new PaymentResponse();
    when(paymentService.createPayment(request, "idem-1"))
      .thenReturn(responseBody);

    ResponseEntity<PaymentResponse> response = paymentController.createPayment(
      request,
      "idem-1"
    );

    assertSame(responseBody, response.getBody());
  }

  @Test
  void downloadReceiptBuildsPngAttachment() {
    PaymentResponse payment = new PaymentResponse();
    payment.setId(7L);
    payment.setEnrollmentId(12L);
    payment.setCurrency("INR");
    payment.setAmount(new BigDecimal("499.00"));
    payment.setStatus(PaymentStatus.SUCCESS);
    payment.setGatewayTransactionId("gtx_123");
    payment.setCreatedAt(LocalDateTime.of(2026, 5, 16, 10, 0));
    when(paymentService.getPayment(7L)).thenReturn(payment);

    ResponseEntity<byte[]> response = paymentController.downloadReceipt(7L);

    assertEquals(
      "attachment; filename=\"payment-receipt-7.png\"",
      response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
    );
    assertEquals("image/png", response.getHeaders().getContentType().toString());
    assertTrue(response.getBody().length > 8);
    assertEquals((byte) 0x89, response.getBody()[0]);
    assertEquals((byte) 0x50, response.getBody()[1]);
    assertEquals((byte) 0x4E, response.getBody()[2]);
    assertEquals((byte) 0x47, response.getBody()[3]);
  }

  @Test
  void getMyPaymentsReturnsServiceResults() {
    List<PaymentResponse> payments = List.of(new PaymentResponse());
    when(paymentService.getPaymentsByUser(5L)).thenReturn(payments);

    ResponseEntity<List<PaymentResponse>> response = paymentController.getMyPayments(
      5L
    );

    assertSame(payments, response.getBody());
  }

  @Test
  void processWebhookDelegatesRequestObject() {
    PaymentWebhookRequest request = new PaymentWebhookRequest();
    PaymentResponse payment = new PaymentResponse();
    when(paymentService.processWebhook(request)).thenReturn(payment);

    ResponseEntity<PaymentResponse> response = paymentController.processWebhook(
      request
    );

    assertSame(payment, response.getBody());
  }

  @Test
  void createRazorpayOrderReturnsServicePayload() {
    RazorpayOrderRequest request = new RazorpayOrderRequest();
    RazorpayOrderResponse order = new RazorpayOrderResponse();
    when(paymentService.createRazorpayOrder(request, 9L, "idem-9"))
      .thenReturn(order);

    ResponseEntity<RazorpayOrderResponse> response = paymentController.createRazorpayOrder(
      request,
      9L,
      "idem-9"
    );

    assertSame(order, response.getBody());
  }

  @Test
  void verifyRazorpayDelegatesUserScopedVerification() {
    RazorpayVerifyRequest request = new RazorpayVerifyRequest();
    PaymentResponse payment = new PaymentResponse();
    when(paymentService.verifyRazorpaySignature(request, 9L))
      .thenReturn(payment);

    ResponseEntity<PaymentResponse> response = paymentController.verifyRazorpay(
      request,
      9L
    );

    assertSame(payment, response.getBody());
  }

  @Test
  void subscribeReturnsSubscriptionResponse() {
    CreateSubscriptionRequest request = new CreateSubscriptionRequest();
    request.setPlan(SubscriptionPlan.MONTHLY);
    request.setPaymentMode(PaymentMode.CARD);
    SubscriptionResponse subscription = new SubscriptionResponse();
    when(paymentService.subscribe(request, 9L, "sub-1"))
      .thenReturn(subscription);

    ResponseEntity<SubscriptionResponse> response = paymentController.subscribe(
      request,
      9L,
      "sub-1"
    );

    assertSame(subscription, response.getBody());
  }

  @Test
  void cancelSubscriptionDelegatesWithActorContext() {
    SubscriptionResponse subscription = new SubscriptionResponse();
    when(paymentService.cancelSubscription(44L, 9L, "ADMIN"))
      .thenReturn(subscription);

    ResponseEntity<SubscriptionResponse> response = paymentController.cancelSubscription(
      44L,
      9L,
      "ADMIN"
    );

    assertSame(subscription, response.getBody());
    verify(paymentService).cancelSubscription(44L, 9L, "ADMIN");
  }
}
