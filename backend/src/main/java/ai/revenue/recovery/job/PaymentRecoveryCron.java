package ai.revenue.recovery.job;

import ai.revenue.recovery.config.AppClock;
import ai.revenue.recovery.entity.AuditLog;
import ai.revenue.recovery.entity.PaymentAttempt;
import ai.revenue.recovery.entity.enums.AuditOutcome;
import ai.revenue.recovery.entity.enums.FlowType;
import ai.revenue.recovery.entity.enums.PaymentStatus;
import ai.revenue.recovery.repository.AuditLogRepository;
import ai.revenue.recovery.repository.PaymentAttemptRepository;
import ai.revenue.recovery.repository.PromiseConfirmationStateRepository;
import ai.revenue.recovery.service.RazorpayIntegrationService;
import com.razorpay.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class PaymentRecoveryCron {

    private static final Logger log = LoggerFactory.getLogger(PaymentRecoveryCron.class);

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final AuditLogRepository auditLogRepository;
    private final RazorpayIntegrationService razorpayService;
    private final ai.revenue.recovery.Whatsapp.WhatsAppLLMService whatsappLLMService;
    private final PromiseConfirmationStateRepository confirmationStateRepository;
    private final ai.revenue.recovery.repository.CustomerRepository customerRepository;

    public PaymentRecoveryCron(PaymentAttemptRepository paymentAttemptRepository,
                               AuditLogRepository auditLogRepository,
                               RazorpayIntegrationService razorpayService,
                               ai.revenue.recovery.Whatsapp.WhatsAppLLMService whatsappLLMService,
                               PromiseConfirmationStateRepository confirmationStateRepository,
                               ai.revenue.recovery.repository.CustomerRepository customerRepository) {
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.auditLogRepository = auditLogRepository;
        this.razorpayService = razorpayService;
        this.whatsappLLMService = whatsappLLMService;
        this.confirmationStateRepository = confirmationStateRepository;
        this.customerRepository = customerRepository;
    }

    /**
     * Autonomous sweeper that resolves stale CREATED/AMBIGUOUS payments.
     * Uses fixedDelay (not fixedRate) to prevent overlapping executions
     * when Razorpay calls take 3-5 seconds per payment.
     */
    @Scheduled(fixedDelay = 15000)
    public void sweepStalePayments() {
        LocalDateTime cutoff = AppClock.now().minusMinutes(15);

        List<PaymentAttempt> stalePayments = paymentAttemptRepository
                .findByStatusInAndInitiatedAtBeforeAndSubscriptionIsNull(
                        List.of(PaymentStatus.CREATED, PaymentStatus.AMBIGUOUS), cutoff);

        if (!stalePayments.isEmpty()) {
            log.info("[AUTONOMOUS_SWEEPER] Found {} stale payments to resolve.", stalePayments.size());
        }

        for (PaymentAttempt attempt : stalePayments) {
            try {
                resolveStalePayment(attempt);
            } catch (Exception e) {
                log.error("[AUTONOMOUS_SWEEPER] Failed to resolve payment ID {}: {}",
                        attempt.getId(), e.getMessage());
                // Continue to next — one failure shouldn't abort the entire sweep
            }
        }

        // Garbage-collect expired PromiseConfirmationState records (older than 24h)
        try {
            confirmationStateRepository.deleteByCreatedAtBefore(AppClock.now().minusHours(24));
        } catch (Exception e) {
            log.warn("[AUTONOMOUS_SWEEPER] Failed to clean expired confirmation states: {}", e.getMessage());
        }
    }

    private void resolveStalePayment(PaymentAttempt attempt) {
        // Skip if already in terminal state (race condition guard)
        if (attempt.getStatus() == PaymentStatus.CAPTURED ||
                attempt.getStatus() == PaymentStatus.FAILED ||
                attempt.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }

        String orderId = attempt.getRazorpayOrderId();
        if (orderId == null) {
            markAsFailedAndNotify(attempt, "NO_ORDER_ID");
            return;
        }

        try {
            Payment payment = razorpayService.fetchPaymentStatus(orderId);

            if (payment == null) {
                markAsFailedAndNotify(attempt, "PAYMENT_NOT_FOUND");
                return;
            }

            String razorpayStatus = payment.get("status");
            attempt.setRazorpayPaymentId(payment.get("id"));

            if ("captured".equalsIgnoreCase(razorpayStatus)) {
                attempt.setStatus(PaymentStatus.CAPTURED);
                attempt.setResolvedAt(AppClock.now());
                
                ai.revenue.recovery.entity.Subscription sub = attempt.getSubscription();
                if (sub != null) {
                    sub.setStatus(ai.revenue.recovery.entity.enums.SubscriptionStatus.ACTIVE);
                    sub.setNextChargeDate(AppClock.now().plusSeconds(sub.getTimeSpan()));
                }
                
                ai.revenue.recovery.entity.Customer customer = attempt.getCustomer();
                if (customer != null) {
                    customer.setRecovered(customer.getRecovered().add(attempt.getAmount()));
                    customerRepository.save(customer);
                }
                
                paymentAttemptRepository.save(attempt);

                writeSweeperAudit(attempt,
                        "[AUTONOMOUS_SWEEPER] Auto-resolved payment ID: " + attempt.getId() + " to CAPTURED",
                        "AUTO_RESOLVED_CAPTURED", AuditOutcome.AUTO_RESOLVED);

                log.info("[AUTONOMOUS_SWEEPER] Payment ID {} auto-resolved to CAPTURED.", attempt.getId());

            } else if ("failed".equalsIgnoreCase(razorpayStatus)) {
                String errorReason = (payment.has("error_reason") && payment.get("error_reason") != null)
                        ? payment.get("error_reason").toString()
                        : "PAYMENT_FAILED";
                markAsFailedAndNotify(attempt, errorReason);

            } else {
                // Still in a non-terminal state at Razorpay — log but don't change
                log.debug("[AUTONOMOUS_SWEEPER] Payment ID {} still in state '{}' at Razorpay, skipping.",
                        attempt.getId(), razorpayStatus);
            }
        } catch (Exception e) {
            log.error("[AUTONOMOUS_SWEEPER] Razorpay API error for payment ID {}: {}",
                    attempt.getId(), e.getMessage());
            throw e; // Re-throw so the outer catch logs and continues
        }
    }

    private void markAsFailedAndNotify(PaymentAttempt attempt, String reasonCode) {
        attempt.setStatus(PaymentStatus.FAILED);
        attempt.setFailureReasonCode(reasonCode);
        attempt.setResolvedAt(AppClock.now());
        
        ai.revenue.recovery.entity.Subscription sub = attempt.getSubscription();
        if (sub != null) {
            sub.setStatus(ai.revenue.recovery.entity.enums.SubscriptionStatus.PAST_DUE);
        }
        
        paymentAttemptRepository.save(attempt);

        writeSweeperAudit(attempt,
                "[AUTONOMOUS_SWEEPER] Auto-resolved payment ID: " + attempt.getId() + " to FAILED (" + reasonCode + ")",
                "AUTO_RESOLVED_FAILED", AuditOutcome.FAILED);

        // Trigger WhatsApp outreach for failed payments
        try {
            if (attempt.getCustomer() != null && attempt.getCustomer().getPhoneNumber() != null) {
                whatsappLLMService.sendPaymentFailedTemplate(
                        attempt.getCustomer(), attempt,
                        "Payment auto-resolved as failed with reason: " + reasonCode);
            }
        } catch (Exception e) {
            log.warn("[AUTONOMOUS_SWEEPER] Failed to send WhatsApp notification for payment ID {}: {}",
                    attempt.getId(), e.getMessage());
        }

        log.info("[AUTONOMOUS_SWEEPER] Payment ID {} auto-resolved to FAILED ({}).",
                attempt.getId(), reasonCode);
    }

    private void writeSweeperAudit(PaymentAttempt attempt, String reasoning, String actionTaken,
                                    AuditOutcome outcome) {
        AuditLog logEntry = AuditLog.builder()
                .flowType(FlowType.AUTONOMOUS_SWEEPER)
                .entityId(attempt.getId())
                .entityType("payment_attempt")
                .decision("AUTONOMOUS_SWEEP")
                .paymentOrderId(attempt.getRazorpayOrderId())
                .reasoning(reasoning)
                .actionTaken(actionTaken)
                .outcome(outcome)
                .attemptNumber(1)
                .createdAt(AppClock.now())
                .build();

        auditLogRepository.save(logEntry);
    }
}
