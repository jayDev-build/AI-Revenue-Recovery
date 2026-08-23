package ai.revenue.recovery.entity;

import ai.revenue.recovery.entity.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_payment_attempt")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPaymentAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    private Integer attemptNumber;

    @Enumerated(EnumType.STRING)
    private AttemptStatus status;

    private String failureReasonCode;
    private LocalDateTime attemptedAt;
    private LocalDateTime nextRetryAt;
    private Boolean whatsappMessageSent;
}
