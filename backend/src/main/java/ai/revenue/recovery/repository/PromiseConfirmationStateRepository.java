package ai.revenue.recovery.repository;

import ai.revenue.recovery.entity.PromiseConfirmationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PromiseConfirmationStateRepository extends JpaRepository<PromiseConfirmationState, Long> {

    Optional<PromiseConfirmationState> findByPhoneNumber(String phoneNumber);

    @Transactional
    void deleteByPhoneNumber(String phoneNumber);

    @Transactional
    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
