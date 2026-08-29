package ai.revenue.recovery.entity.Requests;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SubscriptionRequest {

    private BigDecimal amount;
    private LocalDateTime paymentDateTime;
    private Integer timeSpan;
    private Long customerId;
    private String description;
}
