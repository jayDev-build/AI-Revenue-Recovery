package ai.revenue.recovery.repository;

import ai.revenue.recovery.entity.PromiseToPay;
import ai.revenue.recovery.entity.enums.PromiseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromiseToPayRepository extends JpaRepository<PromiseToPay, Long> {
    List<PromiseToPay> findByCustomerId(Long customerId);
    List<PromiseToPay> findByStatus(PromiseStatus status);
}
