package ai.revenue.recovery.repository;

import ai.revenue.recovery.entity.SubscriptionPaymentAttempt;
import ai.revenue.recovery.entity.enums.AttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionPaymentAttemptRepository extends JpaRepository<SubscriptionPaymentAttempt, Long> {
}
