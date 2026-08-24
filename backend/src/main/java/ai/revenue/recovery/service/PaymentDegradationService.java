package ai.revenue.recovery.service;

import ai.revenue.recovery.entity.AuditLog;
import ai.revenue.recovery.entity.Customer;
import ai.revenue.recovery.entity.PaymentAttempt;
import ai.revenue.recovery.entity.enums.AuditOutcome;
import ai.revenue.recovery.entity.enums.FlowType;
import ai.revenue.recovery.entity.enums.PaymentMethod;
import ai.revenue.recovery.entity.enums.PaymentStatus;
import ai.revenue.recovery.repository.AuditLogRepository;
import ai.revenue.recovery.repository.CustomerRepository;
import ai.revenue.recovery.repository.PaymentAttemptRepository;
import com.razorpay.Payment;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class PaymentDegradationService {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CustomerRepository customerRepository;
    private final AuditLogRepository auditLogRepository;
    private final RazorpayIntegrationService razorpayService;
    private final ChatClient chatClient;

    public PaymentDegradationService(PaymentAttemptRepository paymentAttemptRepository,
                                     CustomerRepository customerRepository,
                                     AuditLogRepository auditLogRepository,
                                     RazorpayIntegrationService razorpayService,
                                     ChatClient.Builder chatClientBuilder) {
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.customerRepository = customerRepository;
        this.auditLogRepository = auditLogRepository;
        this.razorpayService = razorpayService;
        this.chatClient = chatClientBuilder.build();
    }

    public PaymentAttempt initiatePayment(Long customerId, BigDecimal amount, PaymentMethod method) {
        Customer customer = customerRepository.findById(customerId).orElseThrow();

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setCustomer(customer);
        attempt.setAmount(amount);
        attempt.setPaymentMethod(method);
        attempt.setCustomerBank("Bank X"); // For demo purposes
        attempt.setInitiatedAt(LocalDateTime.now());

        try {
            String orderId = razorpayService.createOrder(amount, "receipt_" + System.currentTimeMillis());
            attempt.setRazorpayOrderId(orderId);
            // Simulate some payments getting stuck as AMBIGUOUS immediately (for demo)
            if (Math.random() < 0.15) {
                attempt.setStatus(PaymentStatus.AMBIGUOUS);
            } else {
                attempt.setStatus(PaymentStatus.INITIATED);
            }
        } catch (Exception e) {
            attempt.setStatus(PaymentStatus.FAILED);
            attempt.setFailureReasonCode("ORDER_CREATION_FAILED");
        }
        
        return paymentAttemptRepository.save(attempt);
    }

    public PaymentAttempt getPaymentStatus(Long id) {
        return paymentAttemptRepository.findById(id).orElseThrow();
    }

    public PaymentAttempt resolvePayment(Long id) {
        PaymentAttempt attempt = paymentAttemptRepository.findById(id).orElseThrow();
        
        if (attempt.getStatus() != PaymentStatus.AMBIGUOUS) {
            return attempt;
        }

        try {
            Payment payment = razorpayService.fetchPaymentStatus(attempt.getRazorpayOrderId());
            
            if (payment == null) {
                // Not paid
                markAsFailed(attempt, "PAYMENT_NOT_FOUND");
            } else {
                String status = payment.get("status");
                attempt.setRazorpayPaymentId(payment.get("id"));
                if ("captured".equalsIgnoreCase(status) || "authorized".equalsIgnoreCase(status)) {
                    attempt.setStatus(PaymentStatus.CAPTURED);
                    attempt.setResolvedAt(LocalDateTime.now());
                    paymentAttemptRepository.save(attempt);
                    writeAuditLog(attempt, "STATUS_CHECK", "Payment was successfully captured by Razorpay.", "UPDATED_TO_CAPTURED", AuditOutcome.SUCCESS);
                } else {
                    String errorReason = payment.has("error_reason") ? payment.get("error_reason") : "UNKNOWN_ERROR";
                    markAsFailed(attempt, errorReason);
                }
            }
        } catch (Exception e) {
            markAsFailed(attempt, "API_FETCH_FAILED");
        }

        return attempt;
    }

    private void markAsFailed(PaymentAttempt attempt, String reasonCode) {
        attempt.setStatus(PaymentStatus.FAILED);
        attempt.setFailureReasonCode(reasonCode);
        attempt.setResolvedAt(LocalDateTime.now());
        paymentAttemptRepository.save(attempt);

        String explanation = chatClient.prompt()
                .user("Explain this payment failure reason code in plain, professional language for an audit log: " + reasonCode)
                .call()
                .content();

        writeAuditLog(attempt, "STATUS_CHECK", explanation, "RETRY_SCHEDULED_OR_FAILED", AuditOutcome.FAILED);
    }

    private void writeAuditLog(PaymentAttempt attempt, String decision, String reasoning, String actionTaken, AuditOutcome outcome) {
        AuditLog log = new AuditLog();
        log.setFlowType(FlowType.PAYMENT_DEGRADATION);
        log.setEntityId(attempt.getId());
        log.setEntityType("payment_attempt");
        log.setDecision(decision);
        log.setReasoning(reasoning);
        log.setActionTaken(actionTaken);
        log.setOutcome(outcome);
        log.setAttemptNumber(1);
        log.setCreatedAt(LocalDateTime.now());
        auditLogRepository.save(log);
    }
}
