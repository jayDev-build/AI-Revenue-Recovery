package ai.revenue.recovery.repository;

import ai.revenue.recovery.entity.Subscription;
import ai.revenue.recovery.entity.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByNextChargeDateBeforeAndStatusNot(LocalDateTime now, SubscriptionStatus subscriptionStatus);

    List<Subscription> findByCustomerId(Long customerId);
}
