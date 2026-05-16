package com.olp.payment.repository;

import com.olp.payment.model.PaymentTransaction;
import com.olp.payment.model.PaymentStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository
  extends JpaRepository<PaymentTransaction, Long> {
  Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

  Optional<PaymentTransaction> findByGatewayTransactionId(
    String gatewayTransactionId
  );

  Optional<PaymentTransaction> findFirstByGatewayPaymentId(String gatewayPaymentId);

  java.util.List<PaymentTransaction> findByUserId(Long userId);

  java.util.List<PaymentTransaction> findByCourseId(Long courseId);

  Optional<PaymentTransaction> findFirstByUserIdAndCourseIdAndStatusInOrderByCreatedAtDesc(
    Long userId,
    Long courseId,
    Collection<PaymentStatus> statuses
  );

  java.util.List<PaymentTransaction> findAllByOrderByCreatedAtDesc();
}
