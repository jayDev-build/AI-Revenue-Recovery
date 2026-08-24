package ai.revenue.recovery.entity;

import ai.revenue.recovery.entity.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "subscription")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    private String razorpaySubscriptionId;
    private BigDecimal planAmount;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;

    private LocalDate nextChargeDate;
}
