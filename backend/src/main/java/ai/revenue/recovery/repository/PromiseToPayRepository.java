package ai.revenue.recovery.repository;

import ai.revenue.recovery.entity.PromiseToPay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromiseToPayRepository extends JpaRepository<PromiseToPay, Long> {
}
