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

@Service
public class BankSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(BankSimulatorService.class);

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentService paymentService;

    public BankSimulatorService(PaymentAttemptRepository paymentAttemptRepository,
                                PaymentService paymentService) {
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentService = paymentService;
    }

    /**
     * Step 1: Queues a PENDING payment attempt for the Bank Simulator UI.
     * Razorpay order creation is deferred until bank approval.
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

        return paymentAttemptRepository.save(attempt);
    }

    /**
     * Step 2: Processes bank simulator response.
     * On SUCCESS, hands off execution to PaymentService.
     */
    @Transactional
    public void processBankCallback(BankCallbackPayload payload) {
        if (payload == null || payload.getRazorpayOrderId() == null) {
            throw new IllegalArgumentException("Invalid callback payload");
        }

        PaymentAttempt attempt = paymentAttemptRepository.findByRazorpayOrderId(payload.getRazorpayOrderId());
        if (attempt == null) {
            throw new RuntimeException("Payment attempt not found for order/attempt ID: " + payload.getRazorpayOrderId());
        }

        if (payload.getResponseCode() == BankResponseCode.SUCCESS) {
            log.info("Bank simulator approved payment attempt ID {}. Triggering PaymentService.", attempt.getId());
            paymentService.processApprovedSubscriptionPayment(attempt);
        } else {
            log.warn("Bank simulator rejected payment attempt ID {}", attempt.getId());
            attempt.setStatus(PaymentStatus.FAILED);
            attempt.setFailureReasonCode(payload.getResponseCode() != null ? payload.getResponseCode().name() : "BANK_DECLINED");
            attempt.setResolvedAt(LocalDateTime.now());
            paymentAttemptRepository.save(attempt);
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentAttempt> getPendingBankRequests() {
        return paymentAttemptRepository.findByStatusInAndSubscriptionNotNull(
                List.of(PaymentStatus.CREATED, PaymentStatus.PENDING));
    }
}