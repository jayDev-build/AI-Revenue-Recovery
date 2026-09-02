package ai.revenue.recovery.service;

import ai.revenue.recovery.entity.Customer;
import ai.revenue.recovery.entity.PaymentAttempt;
import ai.revenue.recovery.entity.Requests.SubscriptionRequest;
import ai.revenue.recovery.entity.Subscription;
import ai.revenue.recovery.entity.enums.SubscriptionStatus;
import ai.revenue.recovery.repository.PaymentAttemptRepository;
import ai.revenue.recovery.repository.SubscriptionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final CustomerService customerService;
    private final BankSimulatorService bankSimulatorService;
    private final PaymentService paymentService;
    private final ai.revenue.recovery.repository.CustomerRepository customerRepository;
    private final ai.revenue.recovery.repository.PaymentAttemptRepository paymentAttemptRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               CustomerService customerService,
                               BankSimulatorService bankSimulatorService,
                               PaymentService paymentService,
                               ai.revenue.recovery.repository.CustomerRepository customerRepository,
                               ai.revenue.recovery.repository.PaymentAttemptRepository paymentAttemptRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.customerService = customerService;
        this.bankSimulatorService = bankSimulatorService;
        this.paymentService = paymentService;
        this.customerRepository = customerRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
    }

    /**
     * Creates a new subscription and queues the initial payment attempt into the Bank Simulator.
     */
    @Transactional
    public Subscription createSubscription(SubscriptionRequest request) {
        Customer customer = customerService.getCustomer(request.getCustomerId());

        Subscription subscription = new Subscription();
        subscription.setCustomer(customer);
        subscription.setStatus(SubscriptionStatus.PENDING_ACTIVATION);
        subscription.setPlanAmount(request.getAmount());
        subscription.setDescription(request.getDescription());
        subscription.setTimeSpan(request.getTimeSpan());

        LocalDateTime startDateTime = request.getPaymentDateTime() != null ? request.getPaymentDateTime() : ai.revenue.recovery.config.AppClock.now();
        subscription.setNextChargeDate(startDateTime);

        return subscriptionRepository.save(subscription);
    }

    @Transactional(readOnly = true)
    public Subscription getSubscriptionById(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Subscription not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<Subscription> getAllSubscriptions() {
        return subscriptionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Subscription> getSubscriptionsByCustomerId(Long customerId) {
        return subscriptionRepository.findByCustomerId(customerId);
    }

    @Transactional
    public Subscription updateSubscriptionStatus(Long id, SubscriptionStatus status) {
        Subscription subscription = getSubscriptionById(id);
        subscription.setStatus(status);
        return subscriptionRepository.save(subscription);
    }

    @Transactional
    public Subscription cancelSubscription(Long id) {
        return updateSubscriptionStatus(id, SubscriptionStatus.CANCELLED);
    }

    @Transactional
    public Subscription pauseSubscription(Long id) {
        return updateSubscriptionStatus(id, SubscriptionStatus.PAUSED);
    }



    /**
     * Scheduled / manual trigger to scan and queue payments for subscriptions due for renewal.
     */
    @Transactional
    public void processDueSubscriptions() {
        List<Subscription> dueSubscriptions = subscriptionRepository.findDueSubscriptionsWithNoActiveAttempts(
                ai.revenue.recovery.config.AppClock.now(), 
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE, SubscriptionStatus.PENDING_ACTIVATION));

        for (Subscription subscription : dueSubscriptions) {
            try {
                log.info("Triggering renewal charge for due subscription ID: {}", subscription.getId());
                
                // Do NOT advance nextChargeDate here. It will be advanced upon success, or rescheduled upon failure.
                bankSimulatorService.initiateSubscriptionPayment(subscription);
            } catch (Exception e) {
                log.error("Failed to queue payment attempt for subscription ID {}: {}", subscription.getId(), e.getMessage());
            }
        }
    }

    public List<PaymentAttempt> getAllSubscriptionTransactions() {
        return paymentService.getAllSubscriptionTransactions();
    }

}