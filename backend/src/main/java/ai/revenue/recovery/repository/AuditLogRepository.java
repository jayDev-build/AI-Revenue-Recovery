package ai.revenue.recovery.repository;

import ai.revenue.recovery.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop5ByOrderByCreatedAtDesc();

    @Query("""
            select a from AuditLog a
            where a.paymentOrderId = :orderId
            order by a.attemptNumber desc
            limit 1
            """)
    Optional<AuditLog> getLastAudit(@Param("orderId") String razorpayOrderId);
}
