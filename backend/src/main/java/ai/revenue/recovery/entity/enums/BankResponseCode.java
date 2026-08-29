package ai.revenue.recovery.entity.enums;

import lombok.Getter;

@Getter
public enum BankResponseCode {
    SUCCESS("200", "Transaction approved successfully"),
    INSUFFICIENT_FUNDS("401", "Insufficient funds in customer account"),
    EXPIRED_CARD("402", "Credit/Debit card has expired"),
    BANK_UNRESPONSIVE("503", "Issuer bank server failed to respond"),
    INVALID_PIN("403", "Incorrect PIN or OTP entered"),
    CARD_BLOCKED("405", "Card blocked or flagged by issuer"),
    GATEWAY_TIMEOUT("504", "Bank gateway timed out during processing");

    private final String code;
    private final String description;

    BankResponseCode(String code, String description) {
        this.code = code;
        this.description = description;
    }
}