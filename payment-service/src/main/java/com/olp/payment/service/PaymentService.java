package com.olp.payment.service;

import com.olp.payment.dto.CreatePaymentRequest;
import com.olp.payment.dto.CreateSubscriptionRequest;
import com.olp.payment.dto.PaymentResponse;
import com.olp.payment.dto.PaymentWebhookRequest;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import java.util.UUID;
import java.time.LocalDate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.razorpay.RazorpayClient;
import com.razorpay.Order;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import org.springframework.kafka.core.KafkaTemplate;

@Service
public class PaymentService {

  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

  private final PaymentTransactionRepository repository;
  private final SubscriptionRepository subscriptionRepository;
  private final StringRedisTemplate redisTemplate;
  private final RestTemplate restTemplate;
  private final RazorpayClient razorpayClient;
  private final ObjectMapper objectMapper;
  private final KafkaTemplate<String, String> kafkaTemplate;

  @Value("${razorpay.key-id}")
  private String razorpayKeyId;

  @Value("${razorpay.key-secret}")
  private String razorpaySecret;

  @Value("${razorpay.webhook-secret}")
  private String razorpayWebhookSecret;

  public PaymentService(
    PaymentTransactionRepository repository,
    SubscriptionRepository subscriptionRepository,
    StringRedisTemplate redisTemplate,
    RestTemplate restTemplate,
    RazorpayClient razorpayClient,
    ObjectMapper objectMapper,
    KafkaTemplate<String, String> kafkaTemplate
  ) {
    this.repository = repository;
    this.subscriptionRepository = subscriptionRepository;
    this.redisTemplate = redisTemplate;
    this.restTemplate = restTemplate;
    this.razorpayClient = razorpayClient;
    this.objectMapper = objectMapper;
    this.kafkaTemplate = kafkaTemplate;
  }

  public PaymentResponse createPayment(
    CreatePaymentRequest request,
    String idempotencyKey
  ) {
    String finalKey = (idempotencyKey == null || idempotencyKey.isBlank())
      ? UUID.randomUUID().toString()
      : idempotencyKey;

    PaymentTransaction existing = repository
      .findByIdempotencyKey(finalKey)
      .orElse(null);
    if (existing != null) {
      return mapToResponse(existing);
    }

    String lockKey = "payment:lock:" + finalKey;
    Boolean lockAcquired = redisTemplate
      .opsForValue()
      .setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(30));

    if (Boolean.FALSE.equals(lockAcquired)) {
      throw new PaymentException(
        "Payment request is already being processed for this idempotency key",
        HttpStatus.CONFLICT
      );
    }

    try {
      PaymentTransaction payment = new PaymentTransaction();
      payment.setUserId(request.getUserId());
      payment.setCourseId(request.getCourseId());
      payment.setEnrollmentId(request.getEnrollmentId());
      payment.setAmount(request.getAmount());
      payment.setCurrency(
        request.getCurrency() == null || request.getCurrency().isBlank()
          ? "INR"
          : request.getCurrency().toUpperCase()
      );
      payment.setPaymentMode(request.getPaymentMode());
      payment.setIdempotencyKey(finalKey);
      payment.setPlanReference("COURSE_" + request.getCourseId());
      payment.setStatus(PaymentStatus.PROCESSING);

      // Simulated gateway response (replace with Stripe/Razorpay adapter later)
      payment.setGatewayTransactionId("GTX-" + UUID.randomUUID());
      payment.setStatus(PaymentStatus.SUCCESS);

      PaymentTransaction saved = repository.save(payment);
      redisTemplate
        .opsForValue()
        .set(
          "payment:txn:" + saved.getId(),
          saved.getStatus().name(),
          Duration.ofMinutes(30)
        );

      markEnrollmentPaid(saved.getEnrollmentId());
      publishPaymentEvent(saved, "PAYMENT_SUCCESS");

      return mapToResponse(saved);
    } finally {
      redisTemplate.delete(lockKey);
    }
  }

  public PaymentResponse getPayment(Long paymentId) {
    PaymentTransaction payment = repository
      .findById(paymentId)
      .orElseThrow(() ->
        new PaymentException("Payment not found", HttpStatus.NOT_FOUND)
      );
    return mapToResponse(payment);
  }

  public java.util.List<PaymentResponse> getPaymentsByUser(Long userId) {
      if (userId == null) {
          throw new PaymentException("Missing user id", HttpStatus.UNAUTHORIZED);
      }
      return repository.findByUserId(userId).stream()
              .sorted(Comparator.comparing(PaymentTransaction::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
              .map(this::mapToResponse)
              .collect(java.util.stream.Collectors.toList());
  }

  public java.util.List<PaymentResponse> getAllPayments(String role) {
      requireAdmin(role);
      return repository.findAllByOrderByCreatedAtDesc().stream()
              .map(this::mapToResponse)
              .collect(java.util.stream.Collectors.toList());
  }

  public PaymentResponse processWebhook(PaymentWebhookRequest request) {
    PaymentTransaction payment = repository
      .findByGatewayTransactionId(request.getGatewayTransactionId())
      .orElseThrow(() ->
        new PaymentException(
          "Gateway transaction not found",
          HttpStatus.NOT_FOUND
        )
      );

    PaymentStatus incomingStatus;
    try {
      incomingStatus = PaymentStatus.valueOf(request.getStatus().toUpperCase());
    } catch (Exception ex) {
      throw new PaymentException(
        "Unsupported webhook payment status",
        HttpStatus.BAD_REQUEST
      );
    }

    payment.setStatus(incomingStatus);
    payment.setFailureReason(request.getFailureReason());
    if (request.getGatewayPaymentId() != null && !request.getGatewayPaymentId().isBlank()) {
      payment.setGatewayPaymentId(request.getGatewayPaymentId());
    }
    if (request.getProviderEventId() != null && !request.getProviderEventId().isBlank()) {
      payment.setProviderEventId(request.getProviderEventId());
    }
    if (incomingStatus == PaymentStatus.SUCCESS) {
      ensureEnrollmentActive(payment);
      publishPaymentEvent(payment, "PAYMENT_SUCCESS");
    }
    PaymentTransaction updated = repository.save(payment);
    return mapToResponse(updated);
  }

  private void markEnrollmentPaid(Long enrollmentId) {
    try {
      restTemplate.put(
        "http://enrollment-service/enrollments/" +
        enrollmentId +
        "/status?status=ACTIVE",
        null
      );
    } catch (Exception ex) {
      // intentionally ignored for eventual consistency; can publish to queue later
    }
  }

  public com.olp.payment.dto.RazorpayOrderResponse createRazorpayOrder(
          com.olp.payment.dto.RazorpayOrderRequest request,
          Long userId,
          String idempotencyKey
  ) {
      try {
          ensureRazorpayConfigured();
          if (userId == null) {
              throw new PaymentException("Missing user id", HttpStatus.UNAUTHORIZED);
          }
          if (request.getCourseId() == null) {
              throw new PaymentException("Course id is required", HttpStatus.BAD_REQUEST);
          }
          if (request.getAmount() == null) {
              throw new PaymentException("Amount cannot be null", HttpStatus.BAD_REQUEST);
          }
          if (request.getAmount().signum() <= 0) {
              throw new PaymentException("Amount must be greater than zero", HttpStatus.BAD_REQUEST);
          }

          String finalKey = (idempotencyKey == null || idempotencyKey.isBlank())
                  ? "rzp:" + userId + ":" + request.getCourseId()
                  : idempotencyKey;

          repository.findFirstByUserIdAndCourseIdAndStatusInOrderByCreatedAtDesc(
                  userId,
                  request.getCourseId(),
                  List.of(PaymentStatus.SUCCESS)
          ).ifPresent(existing -> {
              throw new PaymentException("You already have access to this course", HttpStatus.CONFLICT);
          });

          java.util.Optional<PaymentTransaction> processing = repository.findFirstByUserIdAndCourseIdAndStatusInOrderByCreatedAtDesc(
                  userId,
                  request.getCourseId(),
                  List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING)
          );
          if (processing.isPresent()) {
              PaymentTransaction existing = processing.get();
              if (!finalKey.equals(existing.getIdempotencyKey())) {
                  throw new PaymentException("Another payment attempt is already pending for this course", HttpStatus.CONFLICT);
              }
              return buildOrderResponse(existing);
          }

          repository.findByIdempotencyKey(finalKey).ifPresent(existing -> {
              throw new ExistingOrderException(buildOrderResponse(existing));
          });

          BigDecimal normalizedAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
          int amountInPaise = normalizedAmount.multiply(new java.math.BigDecimal("100")).intValueExact();
          JSONObject orderRequest = new JSONObject();
          orderRequest.put("amount", amountInPaise);
          orderRequest.put("currency", "INR");
          orderRequest.put("receipt", "rcpt_" + UUID.randomUUID().toString().substring(0, 8));

          Order order = razorpayClient.orders.create(orderRequest);

          com.olp.payment.dto.RazorpayOrderResponse response = new com.olp.payment.dto.RazorpayOrderResponse();
          response.setRazorpayOrderId(order.get("id"));
          response.setAmount(amountInPaise);
          response.setCurrency("INR");
          response.setKeyId(razorpayKeyId);
          response.setStatus(PaymentStatus.PENDING.name());

          // Save transaction in DB as pending
          PaymentTransaction tx = new PaymentTransaction();
          tx.setUserId(userId);
          tx.setCourseId(request.getCourseId() != null ? request.getCourseId() : 0L);
          tx.setEnrollmentId(0L);
          tx.setAmount(normalizedAmount);
      tx.setCurrency("INR");
      tx.setPaymentMode(PaymentMode.CARD);
      tx.setGatewayTransactionId(order.get("id"));
          tx.setIdempotencyKey(finalKey);
          tx.setPlanReference(request.getPlan());
          tx.setStatus(PaymentStatus.PENDING);
          PaymentTransaction saved = repository.save(tx);
          response.setPaymentId(String.valueOf(saved.getId()));

          return response;
      } catch (ExistingOrderException e) {
          return e.response();
      } catch (PaymentException e) {
          throw e;
      } catch (com.razorpay.RazorpayException e) {
          log.error("Razorpay rejected order creation for user {} and course {}", userId, request.getCourseId(), e);
          throw new PaymentException("Payment gateway could not create the order. Please verify Razorpay credentials and try again.", HttpStatus.BAD_GATEWAY);
      } catch (Exception e) {
          log.error("Failed to create Razorpay order for user {} and course {}", userId, request.getCourseId(), e);
          throw new PaymentException("Failed to create Razorpay order: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
      }
  }

  public PaymentResponse verifyRazorpaySignature(com.olp.payment.dto.RazorpayVerifyRequest request, Long userId) {
      try {
          if (userId == null) {
              throw new PaymentException("Missing user id", HttpStatus.UNAUTHORIZED);
          }
          JSONObject options = new JSONObject();
          options.put("razorpay_order_id", request.getRazorpayOrderId());
          options.put("razorpay_payment_id", request.getRazorpayPaymentId());
          options.put("razorpay_signature", request.getRazorpaySignature());

          boolean isValid = Utils.verifyPaymentSignature(options, razorpaySecret);

          PaymentTransaction tx = repository.findByGatewayTransactionId(request.getRazorpayOrderId())
                  .orElseThrow(() -> new PaymentException("Transaction not found", HttpStatus.NOT_FOUND));

          if (tx.getUserId() != null && userId != null && !tx.getUserId().equals(userId)) {
              throw new PaymentException("Payment does not belong to the current user", HttpStatus.FORBIDDEN);
          }

          if (tx.getStatus() == PaymentStatus.SUCCESS) {
              return mapToResponse(tx);
          }

          if (isValid) {
              tx.setStatus(PaymentStatus.PROCESSING);
              tx.setGatewayPaymentId(request.getRazorpayPaymentId());
              tx.setGatewaySignature(request.getRazorpaySignature());
              tx.setFailureReason(null);
              repository.save(tx);
              ensureEnrollmentActive(tx);
              tx.setStatus(PaymentStatus.SUCCESS);
              repository.save(tx);
              return mapToResponse(tx);
          } else {
              tx.setStatus(PaymentStatus.FAILED);
              tx.setFailureReason("Signature verification failed");
              repository.save(tx);
              throw new PaymentException("Invalid payment signature", HttpStatus.BAD_REQUEST);
          }
      } catch (PaymentException e) {
          throw e;
      } catch (Exception e) {
          log.error("Error verifying Razorpay payment for order {}", request.getRazorpayOrderId(), e);
          throw new PaymentException("Error verifying payment: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
      }
  }

  public PaymentResponse processRazorpayWebhook(String payload, String signature) {
      if (signature == null || signature.isBlank()) {
          throw new PaymentException("Missing Razorpay webhook signature", HttpStatus.UNAUTHORIZED);
      }
      if (!isValidWebhookSignature(payload, signature)) {
          throw new PaymentException("Invalid Razorpay webhook signature", HttpStatus.UNAUTHORIZED);
      }

      try {
          JsonNode root = objectMapper.readTree(payload);
          String eventType = root.path("event").asText();
          JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
          JsonNode refundEntity = root.path("payload").path("refund").path("entity");

          String providerEventId = buildProviderEventId(root, paymentEntity, refundEntity, eventType, payload);

          String webhookKey = "payment:webhook:" + providerEventId;
          Boolean fresh = redisTemplate.opsForValue().setIfAbsent(webhookKey, "processed", Duration.ofHours(12));
          if (Boolean.FALSE.equals(fresh)) {
              PaymentTransaction existing = resolveWebhookTransaction(paymentEntity, refundEntity);
              if (existing == null) {
                  throw new PaymentException("Duplicate webhook already processed", HttpStatus.CONFLICT);
              }
              return mapToResponse(existing);
          }

          PaymentTransaction tx = resolveWebhookTransaction(paymentEntity, refundEntity);
          if (tx == null) {
              throw new PaymentException("Transaction not found for webhook", HttpStatus.NOT_FOUND);
          }

          tx.setProviderEventId(providerEventId);
          if (paymentEntity.hasNonNull("id")) {
              tx.setGatewayPaymentId(paymentEntity.get("id").asText());
          }

          switch (eventType) {
              case "payment.captured", "payment.authorized" -> {
                  tx.setStatus(PaymentStatus.PROCESSING);
                  repository.save(tx);
                  ensureEnrollmentActive(tx);
                  tx.setStatus(PaymentStatus.SUCCESS);
                  tx.setFailureReason(null);
                  publishPaymentEvent(tx, "PAYMENT_SUCCESS");
              }
              case "payment.failed" -> {
                  tx.setStatus(PaymentStatus.FAILED);
                  tx.setFailureReason(paymentEntity.path("error_description").asText("Payment failed at gateway"));
              }
              case "refund.processed", "refund.created" -> {
                  tx.setStatus("refund.processed".equals(eventType) ? PaymentStatus.REFUNDED : PaymentStatus.REFUND_PENDING);
                  if (refundEntity.hasNonNull("id")) {
                      tx.setProviderRefundId(refundEntity.get("id").asText());
                  }
                  if (tx.getStatus() == PaymentStatus.REFUNDED) {
                      publishPaymentEvent(tx, "PAYMENT_REFUNDED");
                  }
              }
              default -> throw new PaymentException("Unsupported Razorpay webhook event: " + eventType, HttpStatus.BAD_REQUEST);
          }

          repository.save(tx);
          return mapToResponse(tx);
      } catch (PaymentException e) {
          throw e;
      } catch (Exception e) {
          log.error("Failed to process Razorpay webhook", e);
          throw new PaymentException("Failed to process Razorpay webhook: " + e.getMessage(), HttpStatus.BAD_REQUEST);
      }
  }

  public PaymentResponse refundPayment(Long paymentId, Long userId, String role) {
      requireAdmin(role);

      PaymentTransaction tx = repository.findById(paymentId)
              .orElseThrow(() -> new PaymentException("Payment not found", HttpStatus.NOT_FOUND));

      if (tx.getStatus() == PaymentStatus.REFUNDED || tx.getStatus() == PaymentStatus.REFUND_PENDING) {
          return mapToResponse(tx);
      }
      if (tx.getStatus() != PaymentStatus.SUCCESS) {
          throw new PaymentException("Only successful payments can be refunded", HttpStatus.CONFLICT);
      }
      if (tx.getGatewayPaymentId() == null || tx.getGatewayPaymentId().isBlank()) {
          throw new PaymentException("Cannot refund a payment that was not captured by the gateway", HttpStatus.CONFLICT);
      }

      tx.setStatus(PaymentStatus.REFUND_PENDING);
      repository.save(tx);

      try {
          ensureRazorpayConfigured();

          HttpHeaders headers = new HttpHeaders();
          headers.setBasicAuth(razorpayKeyId, razorpaySecret);
          headers.setContentType(MediaType.APPLICATION_JSON);
          JSONObject body = new JSONObject();
          body.put("amount", tx.getAmount().multiply(new BigDecimal("100")).intValue());
          body.put("notes", new JSONObject().put("paymentId", tx.getId()).put("requestedBy", userId != null ? userId : 0L));

          Map<String, Object> response = restTemplate.exchange(
                  "https://api.razorpay.com/v1/payments/" + tx.getGatewayPaymentId() + "/refund",
                  HttpMethod.POST,
                  new HttpEntity<>(body.toString(), headers),
                  new ParameterizedTypeReference<Map<String, Object>>() {}
          ).getBody();

          tx.setStatus(PaymentStatus.REFUNDED);
          tx.setFailureReason(null);
          if (response != null && response.get("id") != null) {
              tx.setProviderRefundId(response.get("id").toString());
          }
          repository.save(tx);
          publishPaymentEvent(tx, "PAYMENT_REFUNDED");
          return mapToResponse(tx);
      } catch (Exception e) {
          tx.setStatus(PaymentStatus.SUCCESS);
          tx.setFailureReason("Refund request failed: " + e.getMessage());
          repository.save(tx);
          throw new PaymentException("Refund request failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
      }
  }

  public SubscriptionResponse subscribe(CreateSubscriptionRequest request, Long userId, String idempotencyKey) {
      if (userId == null) {
          throw new PaymentException("Missing user id", HttpStatus.UNAUTHORIZED);
      }
      expirePastSubscriptions();
      SubscriptionPlan plan = request.getPlan();
      if (plan == null) {
          throw new PaymentException("Subscription plan is required", HttpStatus.BAD_REQUEST);
      }

      Subscription existingActive = subscriptionRepository
              .findFirstByUserIdAndStatusOrderByEndDateDesc(userId, SubscriptionStatus.ACTIVE)
              .orElse(null);
      if (existingActive != null && existingActive.getEndDate() != null && existingActive.getEndDate().isBefore(LocalDate.now())) {
          existingActive.setStatus(SubscriptionStatus.EXPIRED);
          subscriptionRepository.save(existingActive);
          existingActive = null;
      }
      if (existingActive != null && existingActive.getPlan() == plan) {
          return mapSubscriptionResponse(existingActive);
      }
      if (existingActive != null && existingActive.getPlan() != SubscriptionPlan.FREE) {
          throw new PaymentException("An active paid subscription already exists", HttpStatus.CONFLICT);
      }
      if (existingActive != null && existingActive.getPlan() == SubscriptionPlan.FREE) {
          existingActive.setStatus(SubscriptionStatus.EXPIRED);
          subscriptionRepository.save(existingActive);
      }

      PaymentMode paymentMode = plan == SubscriptionPlan.FREE ? null : request.getPaymentMode();
      if (plan != SubscriptionPlan.FREE && paymentMode == null) {
          throw new PaymentException("Payment mode is required for paid subscriptions", HttpStatus.BAD_REQUEST);
      }

      BigDecimal amount = getSubscriptionAmount(plan);
      PaymentTransaction payment = null;
      if (plan != SubscriptionPlan.FREE) {
          payment = createSubscriptionPayment(userId, plan, paymentMode, amount, idempotencyKey);
      }

      Subscription subscription = new Subscription();
      subscription.setUserId(userId);
      subscription.setPlan(plan);
      subscription.setStartDate(LocalDate.now());
      subscription.setEndDate(resolveEndDate(plan));
      subscription.setStatus(SubscriptionStatus.ACTIVE);
      subscription.setAmountPaid(amount);
      subscription.setAutoRenew(plan != SubscriptionPlan.FREE && request.isAutoRenew());
      subscription.setPaymentMode(paymentMode);
      subscription.setPaymentTransactionId(payment != null ? payment.getId() : null);

      Subscription saved = subscriptionRepository.save(subscription);
      publishSubscriptionEvent(saved, "SUBSCRIPTION_" + plan.name() + "_ACTIVATED");
      return mapSubscriptionResponse(saved);
  }

  public SubscriptionResponse getMySubscription(Long userId) {
      if (userId == null) {
          throw new PaymentException("Missing user id", HttpStatus.UNAUTHORIZED);
      }
      expirePastSubscriptions();
      return subscriptionRepository.findFirstByUserIdAndStatusOrderByEndDateDesc(userId, SubscriptionStatus.ACTIVE)
              .map(this::mapSubscriptionResponse)
              .orElse(null);
  }

  public List<SubscriptionResponse> getSubscriptionsByUser(Long userId) {
      if (userId == null) {
          throw new PaymentException("Missing user id", HttpStatus.UNAUTHORIZED);
      }
      expirePastSubscriptions();
      return subscriptionRepository.findByUserIdOrderByStartDateDesc(userId).stream()
              .map(this::mapSubscriptionResponse)
              .toList();
  }

  public List<SubscriptionResponse> getAllActiveSubscriptions(String role) {
      requireAdmin(role);
      expirePastSubscriptions();
      return subscriptionRepository.findByStatusOrderByEndDateDesc(SubscriptionStatus.ACTIVE).stream()
              .map(this::mapSubscriptionResponse)
              .toList();
  }

  public SubscriptionResponse cancelSubscription(Long subscriptionId, Long userId, String role) {
      Subscription subscription = subscriptionRepository.findById(subscriptionId)
              .orElseThrow(() -> new PaymentException("Subscription not found", HttpStatus.NOT_FOUND));
      boolean admin = role != null && "ADMIN".equalsIgnoreCase(role);
      if (!admin && (userId == null || !userId.equals(subscription.getUserId()))) {
          throw new PaymentException("You do not have access to this subscription", HttpStatus.FORBIDDEN);
      }
      if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
          return mapSubscriptionResponse(subscription);
      }
      subscription.setStatus(SubscriptionStatus.CANCELLED);
      subscription.setAutoRenew(false);
      Subscription saved = subscriptionRepository.save(subscription);
      publishSubscriptionEvent(saved, "SUBSCRIPTION_CANCELLED");
      return mapSubscriptionResponse(saved);
  }

  private void ensureEnrollmentActive(PaymentTransaction tx) {
      if (tx.getCourseId() == null || tx.getCourseId() <= 0 || tx.getUserId() == null) {
          log.debug("Skipping enrollment activation – no valid courseId/userId on tx {}", tx.getId());
          return;
      }
      try {
          log.info("Activating enrollment for user {} course {}", tx.getUserId(), tx.getCourseId());
          ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                  "http://enrollment-service/enrollments",
                  HttpMethod.POST,
                  new HttpEntity<>(Map.of(
                          "courseId", tx.getCourseId(),
                          "userId", tx.getUserId(),
                          "status", "ACTIVE",
                          "progress", 0
                  )),
                  new ParameterizedTypeReference<Map<String, Object>>() {}
          );
          if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
              throw new PaymentException("Enrollment service did not confirm activation", HttpStatus.BAD_GATEWAY);
          }
          Object enrollmentId = response.getBody().get("id");
          if (enrollmentId == null) {
              throw new PaymentException("Enrollment service returned no enrollment id", HttpStatus.BAD_GATEWAY);
          }
          tx.setEnrollmentId(Long.valueOf(enrollmentId.toString()));
          tx.setFailureReason(null);
          log.info("Enrollment {} activated for user {} course {}", enrollmentId, tx.getUserId(), tx.getCourseId());
      } catch (Exception ex) {
          log.error("Failed to activate enrollment for user {} course {}", tx.getUserId(), tx.getCourseId(), ex);
          tx.setStatus(PaymentStatus.FULFILLMENT_PENDING);
          repository.save(tx);
          if (ex instanceof PaymentException paymentException) {
              throw paymentException;
          }
          throw new PaymentException("Payment captured but enrollment activation failed: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
      }
  }

  private String buildProviderEventId(JsonNode root, JsonNode paymentEntity, JsonNode refundEntity, String eventType, String payload) {
      String paymentId = paymentEntity != null && paymentEntity.hasNonNull("id")
              ? paymentEntity.get("id").asText()
              : "";
      String refundId = refundEntity != null && refundEntity.hasNonNull("id")
              ? refundEntity.get("id").asText()
              : "";
      String createdAt = root.path("created_at").asText("");
      String entityId = !refundId.isBlank() ? refundId : (!paymentId.isBlank() ? paymentId : hashPayload(payload));
      return eventType + ":" + entityId + ":" + createdAt;
  }

  private PaymentResponse mapToResponse(PaymentTransaction transaction) {
    PaymentResponse response = new PaymentResponse();
    response.setId(transaction.getId());
    response.setEnrollmentId(transaction.getEnrollmentId());
    response.setAmount(transaction.getAmount());
    response.setCurrency(transaction.getCurrency());
    response.setPaymentMode(transaction.getPaymentMode());
    response.setStatus(transaction.getStatus());
    response.setGatewayTransactionId(transaction.getGatewayTransactionId());
    response.setRazorpayPaymentId(transaction.getGatewayPaymentId());
    response.setIdempotencyKey(transaction.getIdempotencyKey());
    response.setFailureReason(transaction.getFailureReason());
    response.setCreatedAt(transaction.getCreatedAt());
    response.setUpdatedAt(transaction.getUpdatedAt());
    response.setPlanReference(transaction.getPlanReference());
    response.setProviderRefundId(transaction.getProviderRefundId());
    return response;
  }

  private SubscriptionResponse mapSubscriptionResponse(Subscription subscription) {
      SubscriptionResponse response = new SubscriptionResponse();
      response.setId(subscription.getId());
      response.setUserId(subscription.getUserId());
      response.setPlan(subscription.getPlan());
      response.setStartDate(subscription.getStartDate());
      response.setEndDate(subscription.getEndDate());
      response.setStatus(subscription.getStatus());
      response.setAmountPaid(subscription.getAmountPaid());
      response.setAutoRenew(subscription.isAutoRenew());
      response.setPaymentMode(subscription.getPaymentMode());
      response.setPaymentTransactionId(subscription.getPaymentTransactionId());
      response.setCreatedAt(subscription.getCreatedAt());
      response.setUpdatedAt(subscription.getUpdatedAt());
      return response;
  }

  private com.olp.payment.dto.RazorpayOrderResponse buildOrderResponse(PaymentTransaction transaction) {
      com.olp.payment.dto.RazorpayOrderResponse response = new com.olp.payment.dto.RazorpayOrderResponse();
      response.setRazorpayOrderId(transaction.getGatewayTransactionId());
      response.setAmount(transaction.getAmount().multiply(new java.math.BigDecimal("100")).intValue());
      response.setCurrency(transaction.getCurrency());
      response.setKeyId(razorpayKeyId);
      response.setPaymentId(transaction.getId() != null ? String.valueOf(transaction.getId()) : null);
      response.setStatus(transaction.getStatus() != null ? transaction.getStatus().name() : null);
      return response;
  }

  private void ensureRazorpayConfigured() {
      boolean missingKeyId = razorpayKeyId == null || razorpayKeyId.isBlank();
      boolean missingSecret = razorpaySecret == null || razorpaySecret.isBlank();
      if (missingKeyId || missingSecret) {
          throw new PaymentException(
              "Razorpay is not configured on the server. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET before creating payments.",
              HttpStatus.SERVICE_UNAVAILABLE
          );
      }
  }

  private void requireAdmin(String role) {
      if (role == null || !"ADMIN".equalsIgnoreCase(role)) {
          throw new PaymentException("Admin access required", HttpStatus.FORBIDDEN);
      }
  }

  private PaymentTransaction resolveWebhookTransaction(JsonNode paymentEntity, JsonNode refundEntity) {
      if (paymentEntity != null && paymentEntity.hasNonNull("order_id")) {
          return repository.findByGatewayTransactionId(paymentEntity.get("order_id").asText()).orElse(null);
      }
      if (refundEntity != null && refundEntity.hasNonNull("payment_id")) {
          return repository.findFirstByGatewayPaymentId(refundEntity.get("payment_id").asText()).orElse(null);
      }
      return null;
  }

  private boolean isValidWebhookSignature(String payload, String signature) {
      try {
          String computed = hmacSha256(payload, razorpayWebhookSecret);
          return MessageDigest.isEqual(computed.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
      } catch (Exception e) {
          return false;
      }
  }

  private String hmacSha256(String payload, String secret) throws Exception {
      Mac sha256Hmac = Mac.getInstance("HmacSHA256");
      SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
      sha256Hmac.init(secretKey);
      byte[] hash = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
          sb.append(String.format("%02x", b));
      }
      return sb.toString();
  }

  private String hashPayload(String payload) {
      try {
          MessageDigest digest = MessageDigest.getInstance("SHA-256");
          byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
          StringBuilder sb = new StringBuilder(hash.length * 2);
          for (byte b : hash) {
              sb.append(String.format("%02x", b));
          }
          return sb.toString();
      } catch (Exception e) {
          return UUID.randomUUID().toString();
      }
  }

  private static final class ExistingOrderException extends RuntimeException {
      private final com.olp.payment.dto.RazorpayOrderResponse response;

      private ExistingOrderException(com.olp.payment.dto.RazorpayOrderResponse response) {
          this.response = response;
      }

      private com.olp.payment.dto.RazorpayOrderResponse response() {
          return response;
      }
  }

  private void publishPaymentEvent(PaymentTransaction transaction, String eventType) {
      try {
          Map<String, Object> event = new java.util.HashMap<>();
          event.put("eventType", eventType);
          event.put("paymentId", transaction.getId());
          event.put("userId", transaction.getUserId());
          event.put("courseId", transaction.getCourseId());
          event.put("enrollmentId", transaction.getEnrollmentId());
          event.put("amount", transaction.getAmount());
          event.put("currency", transaction.getCurrency());
          event.put("paymentMode", transaction.getPaymentMode() != null ? transaction.getPaymentMode().name() : null);
          event.put("status", transaction.getStatus() != null ? transaction.getStatus().name() : null);
          event.put("providerRefundId", transaction.getProviderRefundId());
          event.put("timestamp", java.time.LocalDateTime.now().toString());
          kafkaTemplate.send("payment-events", objectMapper.writeValueAsString(event));
      } catch (Exception ex) {
          log.warn("Failed to publish payment event {}", eventType, ex);
      }
  }

  private PaymentTransaction createSubscriptionPayment(Long userId, SubscriptionPlan plan, PaymentMode paymentMode,
                                                       BigDecimal amount, String idempotencyKey) {
      String finalKey = (idempotencyKey == null || idempotencyKey.isBlank())
              ? "subscription:" + userId + ":" + plan.name()
              : idempotencyKey;

      PaymentTransaction existing = repository.findByIdempotencyKey(finalKey).orElse(null);
      if (existing != null) {
          return existing;
      }

      PaymentTransaction payment = new PaymentTransaction();
      payment.setUserId(userId);
      payment.setCourseId(0L);
      payment.setEnrollmentId(0L);
      payment.setAmount(amount);
      payment.setCurrency("INR");
      payment.setPaymentMode(paymentMode);
      payment.setIdempotencyKey(finalKey);
      payment.setPlanReference("SUBSCRIPTION_" + plan.name());
      payment.setStatus(PaymentStatus.SUCCESS);
      payment.setGatewayTransactionId("SUB-" + UUID.randomUUID());
      PaymentTransaction saved = repository.save(payment);
      publishPaymentEvent(saved, "SUBSCRIPTION_PAYMENT_SUCCESS");
      return saved;
  }

  private BigDecimal getSubscriptionAmount(SubscriptionPlan plan) {
      return switch (plan) {
          case FREE -> BigDecimal.ZERO;
          case MONTHLY -> new BigDecimal("499.00");
          case ANNUAL -> new BigDecimal("4999.00");
      };
  }

  private LocalDate resolveEndDate(SubscriptionPlan plan) {
      LocalDate start = LocalDate.now();
      return switch (plan) {
          case FREE -> start.plusYears(10);
          case MONTHLY -> start.plusMonths(1);
          case ANNUAL -> start.plusYears(1);
      };
  }

  private void expirePastSubscriptions() {
      List<Subscription> expired = subscriptionRepository.findByEndDateBeforeAndStatus(LocalDate.now(), SubscriptionStatus.ACTIVE);
      for (Subscription subscription : expired) {
          subscription.setStatus(SubscriptionStatus.EXPIRED);
      }
      if (!expired.isEmpty()) {
          subscriptionRepository.saveAll(expired);
      }
  }

  private void publishSubscriptionEvent(Subscription subscription, String eventType) {
      try {
          Map<String, Object> event = new java.util.HashMap<>();
          event.put("eventType", eventType);
          event.put("subscriptionId", subscription.getId());
          event.put("userId", subscription.getUserId());
          event.put("plan", subscription.getPlan() != null ? subscription.getPlan().name() : null);
          event.put("status", subscription.getStatus() != null ? subscription.getStatus().name() : null);
          event.put("autoRenew", subscription.isAutoRenew());
          event.put("amountPaid", subscription.getAmountPaid());
          event.put("paymentMode", subscription.getPaymentMode() != null ? subscription.getPaymentMode().name() : null);
          event.put("startDate", subscription.getStartDate() != null ? subscription.getStartDate().toString() : null);
          event.put("endDate", subscription.getEndDate() != null ? subscription.getEndDate().toString() : null);
          event.put("timestamp", java.time.LocalDateTime.now().toString());
          kafkaTemplate.send("payment-events", objectMapper.writeValueAsString(event));
      } catch (Exception ex) {
          log.warn("Failed to publish subscription event {}", eventType, ex);
      }
  }
}
