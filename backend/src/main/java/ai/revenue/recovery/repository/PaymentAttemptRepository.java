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
}
