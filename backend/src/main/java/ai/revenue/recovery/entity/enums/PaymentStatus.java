package ai.revenue.recovery.entity.enums;

public enum PaymentStatus {
    CREATED,        //payment requested details note entered
    AUTHORIZED,     //details for payments entered and authorized by razorpay
    AMBIGUOUS,      //Ambiguous status
    CAPTURED,       //payment succeeded
    FAILED,         //payment failed
    PENDING, REFUNDED        //refunded
}
