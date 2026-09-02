package ai.revenue.recovery.service;

import ai.revenue.recovery.entity.Customer;
import ai.revenue.recovery.entity.PaymentAttempt;
import ai.revenue.recovery.entity.Requests.BankCallbackPayload;
import ai.revenue.recovery.entity.Subscription;
import ai.revenue.recovery.entity.enums.BankResponseCode;
import ai.revenue.recovery.entity.enums.PaymentMethod;
import ai.revenue.recovery.entity.enums.PaymentStatus;
import ai.revenue.recovery.repository.PaymentAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class BankSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(BankSimulatorService.class);

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentService paymentService;
    private final RazorpayIntegrationService razorpayService;
    private final ai.revenue.recovery.Whatsapp.WhatsAppLLMService whatsappLLMService;
    private final ai.revenue.recovery.repository.AuditLogRepository auditLogRepository;

    public BankSimulatorService(PaymentAttemptRepository paymentAttemptRepository,
                                PaymentService paymentService,
                                RazorpayIntegrationService razorpayService,
                                ai.revenue.recovery.Whatsapp.WhatsAppLLMService whatsappLLMService,
                                ai.revenue.recovery.repository.AuditLogRepository auditLogRepository) {
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentService = paymentService;
        this.razorpayService = razorpayService;
        this.whatsappLLMService = whatsappLLMService;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Step 1: Generates Razorpay Order ID and queues a PENDING payment attempt
     * for the Bank Simulator UI.
     */
    @Transactional
    public PaymentAttempt initiateSubscriptionPayment(Subscription subscription) {
        if (subscription == null) {
            throw new IllegalArgumentException("Subscription cannot be null");
        }

        Customer customer = subscription.getCustomer();

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setCustomer(customer);
        attempt.setSubscription(subscription);
        attempt.setAmount(subscription.getPlanAmount());
        attempt.setPaymentMethod(PaymentMethod.NETBANKING);
        attempt.setCustomerBank("Bank Simulator");
        attempt.setInitiatedAt(LocalDateTime.now());
        attempt.setStatus(PaymentStatus.PENDING); // Queuing for simulator UI

        try {
            Map<String, String> notes = Map.of(
                    "subscription_id", String.valueOf(subscription.getId()),
                    "customer_id", customer != null ? String.valueOf(customer.getId()) : ""
            );

            String receipt = "sub_rcpt_" + subscription.getId() + "_" + System.currentTimeMillis();

            // Create order on Razorpay to get razorpayOrderId upfront
            String orderId = razorpayService.createOrder(subscription.getPlanAmount(), receipt, notes);
            attempt.setRazorpayOrderId(orderId);

        } catch (Exception e) {
            log.error("Failed to create Razorpay order for subscription ID {}: {}", subscription.getId(), e.getMessage());
            attempt.setStatus(PaymentStatus.FAILED);
            attempt.setFailureReasonCode("ORDER_CREATION_FAILED");
        }

        return paymentAttemptRepository.save(attempt);
    }

    @Transactional
    public void processBankCallback(BankCallbackPayload payload) {
        if (payload == null || payload.getRazorpayOrderId() == null) {
            throw new IllegalArgumentException("Invalid callback payload or missing Razorpay Order ID");
        }

        PaymentAttempt attempt = paymentAttemptRepository.findByRazorpayOrderId(payload.getRazorpayOrderId());
        if (attempt == null) {
            throw new RuntimeException("Payment attempt not found for order ID: " + payload.getRazorpayOrderId());
        }

        if (payload.getResponseCode() == BankResponseCode.SUCCESS) {
            log.info("Bank simulator approved payment attempt ID {}.", attempt.getId());
            attempt.setRazorpayPaymentId(payload.getRazorpayOrderId());
            paymentService.processApprovedSubscriptionPayment(attempt);
        } else {
            log.warn("Bank simulator rejected payment attempt ID {}", attempt.getId());
            attempt.setStatus(PaymentStatus.FAILED);
            String reasonCode = payload.getResponseCode() != null ? payload.getResponseCode().name() : "BANK_DECLINED";
            attempt.setFailureReasonCode(reasonCode);
            attempt.setResolvedAt(LocalDateTime.now());
            paymentAttemptRepository.save(attempt);
            
            Subscription sub = attempt.getSubscription();
            if (sub != null) {
                if (payload.getResponseCode() == BankResponseCode.EXPIRED_CARD || payload.getResponseCode() == BankResponseCode.CARD_BLOCKED) {
                    sub.setStatus(ai.revenue.recovery.entity.enums.SubscriptionStatus.ACTION_REQUIRED);
                    
                    ai.revenue.recovery.entity.AuditLog auditLog = ai.revenue.recovery.entity.AuditLog.builder()
                            .flowType(ai.revenue.recovery.entity.enums.FlowType.SUBSCRIPTION_RECOVERY)
                            .entityId(sub.getId())
                            .entityType("subscription")
                            .decision("TERMINAL_FAILURE_HALT")
                            .paymentOrderId(attempt.getRazorpayOrderId())
                            .reasoning("[SUBSCRIPTION_HALTED] Subscription " + sub.getId() + " suspended due to terminal failure. Awaiting manual user payment.")
                            .actionTaken("SUBSCRIPTION_HALTED")
                            .outcome(ai.revenue.recovery.entity.enums.AuditOutcome.FAILED)
                            .attemptNumber(1)
                            .createdAt(ai.revenue.recovery.config.AppClock.now())
                            .build();
                    auditLogRepository.save(auditLog);
                    log.info("[SUBSCRIPTION_HALTED] Subscription {} suspended due to terminal failure.", sub.getId());
                } else {
                    sub.setStatus(ai.revenue.recovery.entity.enums.SubscriptionStatus.PAST_DUE);
                    // Schedule next retry delay to prevent immediate spamming
                    sub.setNextChargeDate(ai.revenue.recovery.config.AppClock.now().plusSeconds(sub.getTimeSpan()));
                }
            }

            whatsappLLMService.sendSubscriptionFailedTemplate(attempt.getCustomer(), attempt.getSubscription(), reasonCode);
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentAttempt> getPendingBankRequests() {
        return paymentAttemptRepository.findByStatusInAndSubscriptionNotNull(
                List.of(PaymentStatus.CREATED, PaymentStatus.PENDING));
    }
}