package ai.revenue.recovery.entity.Requests;

import ai.revenue.recovery.entity.enums.BankResponseCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BankCallbackPayload {

    private Long subscriptionId;
    private String razorpayOrderId;
//    private String bankTransactionId;
    private BigDecimal amount;
    private BankResponseCode responseCode;
    private String message;
}