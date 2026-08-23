package ai.revenue.recovery.repository;

import ai.revenue.recovery.entity.SubscriptionPaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionPaymentAttemptRepository extends JpaRepository<SubscriptionPaymentAttempt, Long> {
}
