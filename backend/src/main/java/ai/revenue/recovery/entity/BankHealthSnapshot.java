package ai.revenue.recovery.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_health_snapshot")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankHealthSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String bankName;
    private String paymentMethod;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private Integer totalAttempts;
    private Integer successCount;
    private BigDecimal successRate;
    private BigDecimal baselineSuccessRate;
    private Boolean isDegraded;
}
