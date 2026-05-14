package com.olp.payment.repository;

import com.olp.payment.model.Subscription;
import com.olp.payment.model.SubscriptionPlan;
import com.olp.payment.model.SubscriptionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
  List<Subscription> findByUserIdOrderByStartDateDesc(Long userId);

  Optional<Subscription> findFirstByUserIdAndStatusOrderByEndDateDesc(Long userId, SubscriptionStatus status);

  List<Subscription> findByStatusOrderByEndDateDesc(SubscriptionStatus status);

  List<Subscription> findByEndDateBeforeAndStatus(LocalDate date, SubscriptionStatus status);

  long countByPlan(SubscriptionPlan plan);
}
