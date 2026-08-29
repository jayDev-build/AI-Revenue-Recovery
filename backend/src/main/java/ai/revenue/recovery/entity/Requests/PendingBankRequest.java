package ai.revenue.recovery.entity.Requests;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
@Getter
public class PendingBankRequest {
    private Long paymentAttemptId;
    private Long subscriptionId;
    private Long customerId;
    private String description;
    private BigDecimal amount;
    private String razorpayOrderId;
    private LocalDateTime requestedAt;
}