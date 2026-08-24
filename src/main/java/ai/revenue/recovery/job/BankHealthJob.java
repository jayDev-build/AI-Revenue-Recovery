package ai.revenue.recovery.job;

import ai.revenue.recovery.entity.BankHealthSnapshot;
import ai.revenue.recovery.entity.PaymentAttempt;
import ai.revenue.recovery.entity.enums.PaymentStatus;
import ai.revenue.recovery.repository.BankHealthSnapshotRepository;
import ai.revenue.recovery.repository.PaymentAttemptRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class BankHealthJob {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final BankHealthSnapshotRepository bankHealthSnapshotRepository;

    public BankHealthJob(PaymentAttemptRepository paymentAttemptRepository, BankHealthSnapshotRepository bankHealthSnapshotRepository) {
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.bankHealthSnapshotRepository = bankHealthSnapshotRepository;
    }

    // Runs every 60 seconds
    @Scheduled(fixedRate = 60000)
    public void computeBankHealthSnapshots() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneMinuteAgo = now.minusMinutes(1);

        // In a real app we might use a custom JPQL query for aggregation,
        // but for demo readability we fetch and group in memory.
        List<PaymentAttempt> recentAttempts = paymentAttemptRepository.findAll().stream()
                .filter(a -> a.getInitiatedAt().isAfter(oneMinuteAgo))
                .collect(Collectors.toList());

        if (recentAttempts.isEmpty()) {
            return;
        }

        Map<String, List<PaymentAttempt>> attemptsByBank = recentAttempts.stream()
                .collect(Collectors.groupingBy(PaymentAttempt::getCustomerBank));

        for (Map.Entry<String, List<PaymentAttempt>> entry : attemptsByBank.entrySet()) {
            String bank = entry.getKey();
            List<PaymentAttempt> bankAttempts = entry.getValue();

            int totalAttempts = bankAttempts.size();
            long successCount = bankAttempts.stream()
                    .filter(a -> a.getStatus() == PaymentStatus.CAPTURED)
                    .count();

            BigDecimal successRate = new BigDecimal(successCount)
                    .divide(new BigDecimal(totalAttempts), 4, RoundingMode.HALF_UP);
            
            // Mock baseline for demo
            BigDecimal baselineRate = new BigDecimal("0.95");

            // Degraded if success rate is below 80%
            boolean isDegraded = successRate.compareTo(new BigDecimal("0.80")) < 0;

            BankHealthSnapshot snapshot = new BankHealthSnapshot();
            snapshot.setBankName(bank);
            snapshot.setPaymentMethod(bankAttempts.get(0).getPaymentMethod().name());
            snapshot.setWindowStart(oneMinuteAgo);
            snapshot.setWindowEnd(now);
            snapshot.setTotalAttempts(totalAttempts);
            snapshot.setSuccessCount((int) successCount);
            snapshot.setSuccessRate(successRate);
            snapshot.setBaselineSuccessRate(baselineRate);
            snapshot.setIsDegraded(isDegraded);

            bankHealthSnapshotRepository.save(snapshot);
        }
    }
}
