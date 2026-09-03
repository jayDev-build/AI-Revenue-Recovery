package ai.revenue.recovery.repository;

import ai.revenue.recovery.entity.PaymentAttempt;
import ai.revenue.recovery.entity.Requests.PendingBankRequest;
import ai.revenue.recovery.entity.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    PaymentAttempt findByRazorpayOrderId(String orderId);
    PaymentAttempt findFirstByOrderByInitiatedAtDesc();

    List<PaymentAttempt> findByStatus(PaymentStatus paymentStatus);

    List<PaymentAttempt> findByStatusInAndSubscriptionNotNull(List<PaymentStatus> created);

    List<PaymentAttempt> findBySubscriptionNotNullAndStatusNotIn(List<PaymentStatus> pending);

    List<PaymentAttempt> findByStatusInAndInitiatedAtBeforeAndSubscriptionIsNull(
            List<PaymentStatus> statuses, LocalDateTime cutoff);

    List<PaymentAttempt> findBySubscriptionAndStatus(ai.revenue.recovery.entity.Subscription subscription, PaymentStatus status);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE PaymentAttempt p SET p.status = 'FAILED', p.failureReasonCode = :reason, p.resolvedAt = :resolvedAt WHERE p.subscription = :subscription AND p.status = 'PENDING'")
    int supersedePendingAttemptsForSubscription(
            @org.springframework.data.repository.query.Param("subscription") ai.revenue.recovery.entity.Subscription subscription,
            @org.springframework.data.repository.query.Param("reason") String reason,
            @org.springframework.data.repository.query.Param("resolvedAt") LocalDateTime resolvedAt);
}
