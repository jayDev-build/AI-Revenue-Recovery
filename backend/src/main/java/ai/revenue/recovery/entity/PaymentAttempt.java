package ai.revenue.recovery.entity;

import ai.revenue.recovery.entity.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_attempt")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    private String razorpayOrderId;
    private String razorpayPaymentId;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private String customerBank;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @ManyToOne()
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    private String failureReasonCode;
    private LocalDateTime initiatedAt;
    private LocalDateTime resolvedAt;
}
