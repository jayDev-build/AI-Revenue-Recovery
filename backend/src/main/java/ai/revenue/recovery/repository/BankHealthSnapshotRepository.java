package ai.revenue.recovery.repository;

import ai.revenue.recovery.entity.BankHealthSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BankHealthSnapshotRepository extends JpaRepository<BankHealthSnapshot, Long> {
}
