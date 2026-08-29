package ai.revenue.recovery.entity.Requests;

import ai.revenue.recovery.entity.enums.BankResponseCode;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Builder
@Getter
public class BankCallbackPayload {

    private Long subscriptionId;
    private String razorpayOrderId;
    private String bankTransactionId;
    private BigDecimal amount;
    private BankResponseCode responseCode;
    private String message;
}