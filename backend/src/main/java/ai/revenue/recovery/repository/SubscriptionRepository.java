package ai.revenue.recovery.repository;

import ai.revenue.recovery.entity.Subscription;
import ai.revenue.recovery.entity.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    
    @Query("SELECT s FROM Subscription s WHERE s.nextChargeDate <= :now AND s.status IN :statuses " +
           "AND NOT EXISTS (SELECT 1 FROM PaymentAttempt pa WHERE pa.subscription = s AND pa.status IN ('CREATED', 'AMBIGUOUS'))")
    List<Subscription> findDueSubscriptionsWithNoActiveAttempts(@Param("now") LocalDateTime now, @Param("statuses") List<SubscriptionStatus> statuses);

    List<Subscription> findByCustomerId(Long customerId);
}
