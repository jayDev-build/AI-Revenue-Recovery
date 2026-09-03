package ai.revenue.recovery.entity;

import ai.revenue.recovery.entity.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "promise_to_pay")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromiseToPay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    private String relatedEntityType;
    private Long relatedEntityId;

    @Column(columnDefinition = "TEXT")
    private String rawMessage;

    private LocalDate extractedPromiseDate;
    private BigDecimal extractedConfidence;
    private BigDecimal extractedAmount;

    @Enumerated(EnumType.STRING)
    private PromiseStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    @Transient
    private BigDecimal displayAmount;

    @Transient
    private String displayDescription;
}
