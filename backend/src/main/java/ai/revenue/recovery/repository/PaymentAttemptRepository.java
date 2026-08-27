package ai.revenue.recovery.repository;

import ai.revenue.recovery.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    PaymentAttempt findByRazorpayOrderId(String orderId);
    PaymentAttempt findFirstByOrderByInitiatedAtDesc();
}
