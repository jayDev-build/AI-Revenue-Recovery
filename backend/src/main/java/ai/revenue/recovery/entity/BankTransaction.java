package ai.revenue.recovery.entity;

import ai.revenue.recovery.entity.enums.BankResponseCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long subscriptionId;
    private String razorpayOrderId;
    private String bankTransactionId;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private BankResponseCode responseCode;

    private boolean callbackDelivered;
    private LocalDateTime initiatedAt;
    private LocalDateTime processedAt;
}