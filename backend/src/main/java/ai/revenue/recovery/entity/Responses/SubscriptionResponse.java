package ai.revenue.recovery.entity.Responses;

import ai.revenue.recovery.entity.Customer;
import lombok.Builder;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
@Setter
public class SubscriptionResponse {

    private BigDecimal amount;
    private LocalDate nextPayDate;
    private Customer customer;
}
