package ai.revenue.recovery.controller;

import ai.revenue.recovery.config.AppClock;
import ai.revenue.recovery.entity.AuditLog;
import ai.revenue.recovery.entity.Customer;
import ai.revenue.recovery.entity.PaymentAttempt;
import ai.revenue.recovery.entity.enums.*;
import ai.revenue.recovery.repository.AuditLogRepository;
import ai.revenue.recovery.repository.CustomerRepository;
import ai.revenue.recovery.repository.PaymentAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CustomerRepository customerRepository;
    private final AuditLogRepository auditLogRepository;
    private final RazorpayClient razorpayClient;

    public DemoController(PaymentAttemptRepository paymentAttemptRepository,
                          CustomerRepository customerRepository,
                          AuditLogRepository auditLogRepository,
                          @Value("${razorpay.key.id}") String keyId,
                          @Value("${razorpay.key.secret}") String keySecret) throws Exception {
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.customerRepository = customerRepository;
        this.auditLogRepository = auditLogRepository;
        this.razorpayClient = new RazorpayClient(keyId, keySecret);
    }

    /**
     * Injects a pre-aged stale payment for live demo testing.
     * The payment is created with initiatedAt = now - 16 minutes,
     * which exceeds the sweeper's 15-minute staleness threshold.
     * Within ~10 seconds, the PaymentRecoveryCron will pick it up
     * and auto-resolve it.
     */
    @PostMapping("/inject-stale-payment")
    public ResponseEntity<PaymentAttempt> injectStalePayment(@RequestBody(required = false) Map<String, Object> request) {
        // Extract optional parameters with defaults
        Long customerId = null;
        BigDecimal amount = new BigDecimal("500");
        String bankName = "HDFC UPI";

        if (request != null) {
            if (request.containsKey("customerId")) {
                customerId = Long.valueOf(request.get("customerId").toString());
            }
            if (request.containsKey("amount")) {
                amount = new BigDecimal(request.get("amount").toString());
            }
            if (request.containsKey("bankName")) {
                bankName = request.get("bankName").toString();
            }
        }

        // Find customer — use provided ID or fall back to first available
        Customer customer;
        final Long resolvedCustomerId = customerId;
        if (resolvedCustomerId != null) {
            customer = customerRepository.findById(resolvedCustomerId)
                    .orElseThrow(() -> new RuntimeException("Customer not found with ID: " + resolvedCustomerId));
        } else {
            customer = customerRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new RuntimeException("No customers exist in the database. Seed data first."));
        }

        // Create a live order on Razorpay to prevent cron crash
        String validOrderId = null;
        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amount.multiply(new BigDecimal("100")).intValue());
            orderRequest.put("currency", "INR");
            Order realRazorpayOrder = razorpayClient.orders.create(orderRequest);
            validOrderId = realRazorpayOrder.get("id");
        } catch (Exception e) {
            log.error("Failed to create live Razorpay order for demo injection: {}", e.getMessage());
            throw new RuntimeException("Demo injection failed due to Razorpay error.", e);
        }

        // Create the stale payment — 16 minutes old to exceed the 15-minute threshold
        PaymentAttempt attempt = PaymentAttempt.builder()
                .customer(customer)
                .amount(amount)
                .paymentMethod(PaymentMethod.UPI)
                .customerBank(bankName)
                .status(PaymentStatus.AMBIGUOUS)
                .razorpayOrderId(validOrderId)
                .initiatedAt(AppClock.now().minusMinutes(16))
                .build();

        PaymentAttempt saved = paymentAttemptRepository.save(attempt);

        // Write audit log
        AuditLog auditLog = AuditLog.builder()
                .flowType(FlowType.AUTONOMOUS_SWEEPER)
                .entityId(saved.getId())
                .entityType("payment_attempt")
                .decision("DEMO_INJECTION")
                .paymentOrderId(saved.getRazorpayOrderId())
                .reasoning("[DEMO_TRIGGER] Injected 16-minute-old stale payment (ID: " + saved.getId() + ")")
                .actionTaken("INJECTED_STALE_PAYMENT")
                .outcome(AuditOutcome.PENDING)
                .attemptNumber(0)
                .createdAt(AppClock.now())
                .build();

        auditLogRepository.save(auditLog);

        log.info("[DEMO_TRIGGER] Injected 16-minute-old stale payment (ID: {}). " +
                "Sweeper will auto-resolve within ~10 seconds.", saved.getId());

        return ResponseEntity.ok(saved);
    }
}
