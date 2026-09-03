package ai.revenue.recovery.controller;

import ai.revenue.recovery.entity.PromiseToPay;
import ai.revenue.recovery.entity.Subscription;
import ai.revenue.recovery.entity.enums.PromiseStatus;
import ai.revenue.recovery.repository.PromiseToPayRepository;
import ai.revenue.recovery.service.RazorpayIntegrationService;
import ai.revenue.recovery.service.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/promises")
public class PromiseToPayController {

    private final PromiseToPayRepository promiseToPayRepository;
    private final RazorpayIntegrationService razorpayService;
    private final SubscriptionService subscriptionService;
    private final ai.revenue.recovery.repository.CustomerRepository customerRepository;
    private final ai.revenue.recovery.repository.SubscriptionRepository subscriptionRepository;

    public PromiseToPayController(PromiseToPayRepository promiseToPayRepository,
            RazorpayIntegrationService razorpayService,
            SubscriptionService subscriptionService,
            ai.revenue.recovery.repository.CustomerRepository customerRepository,
            ai.revenue.recovery.repository.SubscriptionRepository subscriptionRepository) {
        this.promiseToPayRepository = promiseToPayRepository;
        this.razorpayService = razorpayService;
        this.subscriptionService = subscriptionService;
        this.customerRepository = customerRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<PromiseToPay>> getPromisesByCustomer(@PathVariable Long customerId) {
        List<PromiseToPay> promises = promiseToPayRepository.findByCustomerId(customerId)
                .stream()
                .filter(p -> p.getStatus() == PromiseStatus.PENDING)
                .toList();

        for (PromiseToPay p : promises) {
            BigDecimal amount = new BigDecimal("500.00");
            String description = "Promise to Pay";
            if ("SUBSCRIPTION".equals(p.getRelatedEntityType()) && p.getRelatedEntityId() != null) {
                try {
                    Subscription sub = subscriptionService.getSubscriptionById(p.getRelatedEntityId());
                    if (sub != null) {
                        amount = sub.getPlanAmount();
                        description = sub.getDescription() != null ? sub.getDescription() : "Subscription Plan";
                    }
                } catch (Exception e) {
                }
            }
            if (p.getExtractedAmount() != null) {
                amount = p.getExtractedAmount();
            }
            p.setDisplayAmount(amount);
            p.setDisplayDescription(description);
        }

        return ResponseEntity.ok(promises);
    }

    @PostMapping("/{promiseId}/initiate-payment")
    public ResponseEntity<?> initiatePayment(@PathVariable Long promiseId) {
        Optional<PromiseToPay> optionalPromise = promiseToPayRepository.findById(promiseId);
        if (optionalPromise.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        PromiseToPay promise = optionalPromise.get();
        // Default amount if linked subscription is not found, else use subscription
        // amount
        BigDecimal amount = new BigDecimal("500.00");
        if ("SUBSCRIPTION".equals(promise.getRelatedEntityType()) && promise.getRelatedEntityId() != null) {
            Subscription sub = subscriptionService.getSubscriptionById(promise.getRelatedEntityId());
            amount = sub.getPlanAmount();
        }

        try {
            String receipt = "prom_rcpt_" + promise.getId() + "_" + System.currentTimeMillis();
            String orderId = razorpayService.createOrder(amount, receipt, Map.of(
                    "promise_id", String.valueOf(promise.getId())));
            return ResponseEntity.ok(Map.of(
                    "razorpayOrderId", orderId,
                    "amount", amount));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to create Razorpay order: " + e.getMessage());
        }
    }

    @PostMapping("/{promiseId}/verify")
    public ResponseEntity<?> verifyPayment(@PathVariable Long promiseId) {
        Optional<PromiseToPay> optionalPromise = promiseToPayRepository.findById(promiseId);
        if (optionalPromise.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        PromiseToPay promise = optionalPromise.get();
        promise.setStatus(PromiseStatus.KEPT);
        promise.setResolvedAt(LocalDateTime.now());

        BigDecimal amount = new BigDecimal("500.00");

        if ("SUBSCRIPTION".equals(promise.getRelatedEntityType()) && promise.getRelatedEntityId() != null) {
            Subscription sub = subscriptionService.getSubscriptionById(promise.getRelatedEntityId());
            if (sub != null) {
                amount = sub.getPlanAmount();
                sub.setStatus(ai.revenue.recovery.entity.enums.SubscriptionStatus.ACTIVE);
                sub.setNextChargeDate(LocalDateTime.now().plusSeconds(sub.getTimeSpan()));
                subscriptionRepository.save(sub);
            }
        }

        if (promise.getExtractedAmount() != null) {
            amount = promise.getExtractedAmount();
        }

        ai.revenue.recovery.entity.Customer customer = promise.getCustomer();
        if (customer != null) {
            BigDecimal currentRecovered = customer.getRecovered() != null ? customer.getRecovered() : BigDecimal.ZERO;
            customer.setRecovered(currentRecovered.add(amount));
            customerRepository.save(customer);
        }

        promiseToPayRepository.save(promise);
        return ResponseEntity.ok(Map.of("status", "KEPT"));
    }

}
