package com.olp.payment.service;

import com.olp.payment.dto.CreateSubscriptionRequest;
import com.olp.payment.dto.PaymentResponse;
import com.olp.payment.dto.RazorpayOrderRequest;
import com.olp.payment.dto.RazorpayOrderResponse;
import com.olp.payment.dto.RazorpayVerifyRequest;
import com.olp.payment.dto.SubscriptionResponse;
import com.olp.payment.exception.PaymentException;
import com.olp.payment.model.PaymentMode;
import com.olp.payment.model.PaymentStatus;
import com.olp.payment.model.PaymentTransaction;
import com.olp.payment.model.Subscription;
import com.olp.payment.model.SubscriptionPlan;
import com.olp.payment.model.SubscriptionStatus;
import com.olp.payment.repository.PaymentTransactionRepository;
import com.olp.payment.repository.SubscriptionRepository;
import com.razorpay.RazorpayClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentTransactionRepository repository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RazorpayClient razorpayClient;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(repository, subscriptionRepository, redisTemplate, restTemplate, razorpayClient, new ObjectMapper(), kafkaTemplate);
        ReflectionTestUtils.setField(paymentService, "razorpayKeyId", "rzp_test_123");
        ReflectionTestUtils.setField(paymentService, "razorpaySecret", "secret");
        ReflectionTestUtils.setField(paymentService, "razorpayWebhookSecret", "webhook-secret");
    }

    @Test
    void getPaymentsByUserRequiresAuthenticatedUser() {
        PaymentException ex = assertThrows(PaymentException.class, () -> paymentService.getPaymentsByUser(null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(repository, never()).findByUserId(null);
    }

    @Test
    void getPaymentsByUserReturnsMappedResponses() {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(12L);
        tx.setAmount(new BigDecimal("1999"));
        tx.setCurrency("INR");
        tx.setStatus(PaymentStatus.SUCCESS);
        when(repository.findByUserId(4L)).thenReturn(List.of(tx));

        List<PaymentResponse> responses = paymentService.getPaymentsByUser(4L);

        assertEquals(1, responses.size());
        assertEquals(12L, responses.get(0).getId());
        assertEquals(PaymentStatus.SUCCESS, responses.get(0).getStatus());
    }

    @Test
    void verifyRazorpayRequiresAuthenticatedUser() {
        RazorpayVerifyRequest request = new RazorpayVerifyRequest();
        request.setRazorpayOrderId("order_1");

        PaymentException ex = assertThrows(
                PaymentException.class,
                () -> paymentService.verifyRazorpaySignature(request, null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(repository, never()).findByGatewayTransactionId("order_1");
    }

    @Test
    void verifyRazorpayBlocksCrossUserAccess() {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setUserId(7L);
        tx.setStatus(PaymentStatus.PROCESSING);
        when(repository.findByGatewayTransactionId("order_2")).thenReturn(Optional.of(tx));

        RazorpayVerifyRequest request = new RazorpayVerifyRequest();
        request.setRazorpayOrderId("order_2");
        request.setRazorpayPaymentId("pay_2");
        request.setRazorpaySignature("sig");

        PaymentException ex = assertThrows(
                PaymentException.class,
                () -> paymentService.verifyRazorpaySignature(request, 9L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void verifyRazorpaySuccessRestoresEnrollmentBeforeReturning() {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setUserId(4L);
        tx.setEnrollmentId(55L);
        tx.setStatus(PaymentStatus.SUCCESS);
        when(repository.findByGatewayTransactionId("order_3")).thenReturn(Optional.of(tx));

        RazorpayVerifyRequest request = new RazorpayVerifyRequest();
        request.setRazorpayOrderId("order_3");

        PaymentResponse response = paymentService.verifyRazorpaySignature(request, 4L);

        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        verify(restTemplate).put("http://enrollment-service/enrollments/55/status?status=ACTIVE", null);
    }

    @Test
    void createRazorpayOrderSuccessConflictRestoresEnrollmentAccess() {
        PaymentTransaction existing = new PaymentTransaction();
        existing.setUserId(4L);
        existing.setCourseId(1L);
        existing.setEnrollmentId(55L);
        existing.setStatus(PaymentStatus.SUCCESS);

        RazorpayOrderRequest request = new RazorpayOrderRequest();
        request.setCourseId(1L);
        request.setAmount(new BigDecimal("399.00"));
        request.setPlan("COURSE_1");

        when(repository.findFirstByUserIdAndCourseIdAndStatusInOrderByCreatedAtDesc(eq(4L), eq(1L), any()))
                .thenReturn(Optional.of(existing));

        PaymentException ex = assertThrows(
                PaymentException.class,
                () -> paymentService.createRazorpayOrder(request, 4L, "4-course-1")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(restTemplate).put("http://enrollment-service/enrollments/55/status?status=ACTIVE", null);
    }

    @Test
    void getAllPaymentsRequiresAdmin() {
        PaymentException ex = assertThrows(PaymentException.class, () -> paymentService.getAllPayments("STUDENT"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void refundPaymentRequiresSuccessfulCapture() {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(15L);
        tx.setStatus(PaymentStatus.FAILED);
        when(repository.findById(15L)).thenReturn(Optional.of(tx));

        PaymentException ex = assertThrows(PaymentException.class, () -> paymentService.refundPayment(15L, 1L, "ADMIN"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void refundPaymentRequiresAdminRole() {
        PaymentException ex = assertThrows(PaymentException.class, () -> paymentService.refundPayment(1L, 1L, "INSTRUCTOR"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void refundPaymentMarksCapturedPaymentRefunded() {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(16L);
        tx.setUserId(4L);
        tx.setCourseId(20L);
        tx.setEnrollmentId(30L);
        tx.setAmount(new BigDecimal("499.00"));
        tx.setCurrency("INR");
        tx.setPaymentMode(PaymentMode.CARD);
        tx.setStatus(PaymentStatus.SUCCESS);
        tx.setGatewayPaymentId("pay_16");
        tx.setIdempotencyKey("idem-16");

        when(repository.findById(16L)).thenReturn(Optional.of(tx));
        when(repository.save(org.mockito.ArgumentMatchers.any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(restTemplate.exchange(
                org.mockito.ArgumentMatchers.eq("https://api.razorpay.com/v1/payments/pay_16/refund"),
                org.mockito.ArgumentMatchers.eq(org.springframework.http.HttpMethod.POST),
                org.mockito.ArgumentMatchers.any(org.springframework.http.HttpEntity.class),
                org.mockito.ArgumentMatchers.<org.springframework.core.ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenReturn(ResponseEntity.ok(Map.of("id", "rfnd_16")));

        PaymentResponse response = paymentService.refundPayment(16L, 1L, "ADMIN");

        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
        assertEquals("rfnd_16", response.getProviderRefundId());
        verify(repository, times(2)).save(tx);
    }

    @Test
    void subscribeCreatesFreePlanWithoutPaymentTransaction() {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest();
        request.setPlan(SubscriptionPlan.FREE);

        when(subscriptionRepository.findByEndDateBeforeAndStatus(org.mockito.ArgumentMatchers.any(LocalDate.class), org.mockito.ArgumentMatchers.eq(SubscriptionStatus.ACTIVE)))
                .thenReturn(List.of());
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByEndDateDesc(4L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(org.mockito.ArgumentMatchers.any(Subscription.class)))
                .thenAnswer(invocation -> {
                    Subscription subscription = invocation.getArgument(0);
                    subscription.setId(31L);
                    return subscription;
                });

        SubscriptionResponse response = paymentService.subscribe(request, 4L, null);

        assertEquals(31L, response.getId());
        assertEquals(SubscriptionPlan.FREE, response.getPlan());
        assertEquals(SubscriptionStatus.ACTIVE, response.getStatus());
    }

    @Test
    void subscribeRequiresPaymentModeForPaidPlan() {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest();
        request.setPlan(SubscriptionPlan.MONTHLY);

        when(subscriptionRepository.findByEndDateBeforeAndStatus(org.mockito.ArgumentMatchers.any(LocalDate.class), org.mockito.ArgumentMatchers.eq(SubscriptionStatus.ACTIVE)))
                .thenReturn(List.of());
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByEndDateDesc(4L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        PaymentException ex = assertThrows(PaymentException.class, () -> paymentService.subscribe(request, 4L, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void subscribeCreatesPaidPlanWithPaymentTransaction() {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest();
        request.setPlan(SubscriptionPlan.MONTHLY);
        request.setPaymentMode(PaymentMode.CARD);
        request.setAutoRenew(true);

        when(subscriptionRepository.findByEndDateBeforeAndStatus(org.mockito.ArgumentMatchers.any(LocalDate.class), org.mockito.ArgumentMatchers.eq(SubscriptionStatus.ACTIVE)))
                .thenReturn(List.of());
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByEndDateDesc(4L, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.findByIdempotencyKey("sub-idem"))
                .thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(PaymentTransaction.class)))
                .thenAnswer(invocation -> {
                    PaymentTransaction payment = invocation.getArgument(0);
                    payment.setId(88L);
                    return payment;
                });
        when(subscriptionRepository.save(org.mockito.ArgumentMatchers.any(Subscription.class)))
                .thenAnswer(invocation -> {
                    Subscription subscription = invocation.getArgument(0);
                    subscription.setId(99L);
                    return subscription;
                });

        SubscriptionResponse response = paymentService.subscribe(request, 4L, "sub-idem");

        assertEquals(99L, response.getId());
        assertEquals(SubscriptionPlan.MONTHLY, response.getPlan());
        assertEquals(new BigDecimal("499.00"), response.getAmountPaid());
        assertEquals(PaymentMode.CARD, response.getPaymentMode());
        assertEquals(88L, response.getPaymentTransactionId());
        assertEquals(true, response.isAutoRenew());
    }

    @Test
    void getMySubscriptionRequiresUser() {
        PaymentException ex = assertThrows(PaymentException.class, () -> paymentService.getMySubscription(null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void getAllActiveSubscriptionsRequiresAdminRole() {
        PaymentException ex = assertThrows(PaymentException.class, () -> paymentService.getAllActiveSubscriptions("STUDENT"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void cancelSubscriptionAllowsOwnerAndDisablesAutoRenew() {
        Subscription subscription = new Subscription();
        subscription.setId(44L);
        subscription.setUserId(9L);
        subscription.setPlan(SubscriptionPlan.ANNUAL);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setAutoRenew(true);
        subscription.setAmountPaid(new BigDecimal("4999.00"));

        when(subscriptionRepository.findById(44L)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(org.mockito.ArgumentMatchers.any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionResponse response = paymentService.cancelSubscription(44L, 9L, "STUDENT");

        assertEquals(SubscriptionStatus.CANCELLED, response.getStatus());
        assertEquals(false, response.isAutoRenew());
    }

    @Test
    void cancelSubscriptionRejectsDifferentUser() {
        Subscription subscription = new Subscription();
        subscription.setId(45L);
        subscription.setUserId(10L);
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        when(subscriptionRepository.findById(45L)).thenReturn(Optional.of(subscription));

        PaymentException ex = assertThrows(
                PaymentException.class,
                () -> paymentService.cancelSubscription(45L, 99L, "STUDENT")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void createRazorpayOrderRejectsWhenSuccessfulCoursePurchaseAlreadyExists() {
        RazorpayOrderRequest request = new RazorpayOrderRequest();
        request.setCourseId(14L);
        request.setAmount(new BigDecimal("999.00"));

        PaymentTransaction existing = new PaymentTransaction();
        existing.setId(41L);
        existing.setUserId(6L);
        existing.setCourseId(14L);
        existing.setStatus(PaymentStatus.SUCCESS);

        when(repository.findFirstByUserIdAndCourseIdAndStatusInOrderByCreatedAtDesc(
                6L,
                14L,
                List.of(PaymentStatus.SUCCESS)
        )).thenReturn(Optional.of(existing));

        PaymentException ex = assertThrows(
                PaymentException.class,
                () -> paymentService.createRazorpayOrder(request, 6L, "idem-14")
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("You already have access to this course", ex.getMessage());
    }

    @Test
    void createRazorpayOrderReturnsExistingPendingOrderForSameCourse() {
        RazorpayOrderRequest request = new RazorpayOrderRequest();
        request.setCourseId(14L);
        request.setAmount(new BigDecimal("999.00"));

        PaymentTransaction existing = new PaymentTransaction();
        existing.setId(52L);
        existing.setUserId(6L);
        existing.setCourseId(14L);
        existing.setAmount(new BigDecimal("999.00"));
        existing.setCurrency("INR");
        existing.setStatus(PaymentStatus.PENDING);
        existing.setGatewayTransactionId("order_existing");
        existing.setIdempotencyKey("idem-14");

        when(repository.findFirstByUserIdAndCourseIdAndStatusInOrderByCreatedAtDesc(
                6L,
                14L,
                List.of(PaymentStatus.SUCCESS)
        )).thenReturn(Optional.empty());
        when(repository.findFirstByUserIdAndCourseIdAndStatusInOrderByCreatedAtDesc(
                6L,
                14L,
                List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING)
        )).thenReturn(Optional.of(existing));

        RazorpayOrderResponse response = paymentService.createRazorpayOrder(request, 6L, "idem-14");

        assertEquals("order_existing", response.getRazorpayOrderId());
        assertEquals("52", response.getPaymentId());
        assertEquals(PaymentStatus.PENDING.name(), response.getStatus());
    }
}
