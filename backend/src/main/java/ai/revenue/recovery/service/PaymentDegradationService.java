package ai.revenue.recovery.service;

import ai.revenue.recovery.entity.AuditLog;
import ai.revenue.recovery.entity.BankHealthSnapshot;
import ai.revenue.recovery.entity.Customer;
import ai.revenue.recovery.entity.PaymentAttempt;
import ai.revenue.recovery.entity.enums.AuditOutcome;
import ai.revenue.recovery.entity.enums.FlowType;
import ai.revenue.recovery.entity.enums.PaymentMethod;
import ai.revenue.recovery.entity.enums.PaymentStatus;
import ai.revenue.recovery.repository.AuditLogRepository;
import ai.revenue.recovery.repository.BankHealthSnapshotRepository;
import ai.revenue.recovery.repository.CustomerRepository;
import ai.revenue.recovery.repository.PaymentAttemptRepository;
import com.razorpay.Payment;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentDegradationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentDegradationService.class);

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CustomerRepository customerRepository;
    private final BankHealthSnapshotRepository bankHealthSnapshotRepository;
    private final AuditLogRepository auditLogRepository;
    private final RazorpayIntegrationService razorpayService;
    private final ChatClient chatClient;

    public PaymentDegradationService(BankHealthSnapshotRepository bankHealthSnapshotRepository,
                                     PaymentAttemptRepository paymentAttemptRepository,
                                     CustomerRepository customerRepository,
                                     AuditLogRepository auditLogRepository,
                                     RazorpayIntegrationService razorpayService,
                                     ChatClient.Builder chatClientBuilder) {
        this.bankHealthSnapshotRepository = bankHealthSnapshotRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.customerRepository = customerRepository;
        this.auditLogRepository = auditLogRepository;
        this.razorpayService = razorpayService;
        this.chatClient = chatClientBuilder.defaultSystem("""
            You are a payment database logger. Explain raw payment failure codes based on these states:
            - Created: Request made, details unprocessed.
            - Authorized: Funds approved, not captured.
            - Captured: Payment complete and verified.
            - Failed: Transaction failed, needs retry.
            - Refunded: Captured amount reversed.
            
            CRITICAL: Provide a single, plain phrase under 15 words for a MySQL VARCHAR column. No punctuation, no markdown, no fluff.
        """).build();
    }

    @Transactional
    public PaymentAttempt initiatePayment(Long customerId, BigDecimal amount, PaymentMethod method, String bankName,
                                          boolean simulateDrop) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found with ID: " + customerId));

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setCustomer(customer);
        attempt.setAmount(amount);
        attempt.setPaymentMethod(method);
        attempt.setCustomerBank(bankName != null ? bankName : "Bank X");
        attempt.setInitiatedAt(LocalDateTime.now());

        try {
            Map<String, String> notes = Map.of(
                    "bank_name", attempt.getCustomerBank(),
                    "simulate_drop", String.valueOf(simulateDrop));
            String orderId = razorpayService.createOrder(amount, "receipt_" + System.currentTimeMillis(), notes);
            attempt.setRazorpayOrderId(orderId);

            if (Math.random() < 0.15 || simulateDrop) {
                attempt.setStatus(PaymentStatus.AMBIGUOUS);
            } else {
                attempt.setStatus(PaymentStatus.CREATED);
            }
        } catch (Exception e) {
            log.error("Failed to create Razorpay order: {}", e.getMessage());
            attempt.setStatus(PaymentStatus.FAILED);
            attempt.setFailureReasonCode("ORDER_CREATION_FAILED");
        }

        return paymentAttemptRepository.save(attempt);
    }

    @Transactional(readOnly = true)
    public PaymentAttempt getPaymentStatus(Long id) {
        return paymentAttemptRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PaymentAttempt not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public PaymentAttempt getLatestPayment() {
        return paymentAttemptRepository.findFirstByOrderByInitiatedAtDesc();
    }

    @Transactional
    public PaymentAttempt resolvePayment(Long id) {
        PaymentAttempt attempt = paymentAttemptRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PaymentAttempt not found with ID: " + id));

        // 1. Skip terminal states
        if (attempt.getStatus() == PaymentStatus.CAPTURED ||
                attempt.getStatus() == PaymentStatus.FAILED ||
                attempt.getStatus() == PaymentStatus.REFUNDED) {
            return attempt;
        }

        // 2. Check attempt limit BEFORE invoking external APIs (with NPE guard)
        Optional<AuditLog> lastAudit = auditLogRepository.getLastAudit(attempt.getRazorpayOrderId());
        if (lastAudit.isPresent() && Integer.valueOf(5).equals(lastAudit.get().getAttemptNumber())) {
            attempt.setStatus(PaymentStatus.FAILED);
            attempt.setResolvedAt(LocalDateTime.now());
            writeAuditLog(
                    attempt,
                    "ATTEMPT_LIMIT_REACHED",
                    "Maximum resolution attempt limit reached (5/5)",
                    "MARKED_AS_FAILED",
                    AuditOutcome.ATTEMPT_LIMIT_REACHED
            );
            return paymentAttemptRepository.save(attempt);
        }

        try {
            Payment payment = razorpayService.fetchPaymentStatus(attempt.getRazorpayOrderId());

            if (payment == null) {
                if (attempt.getStatus() == PaymentStatus.AMBIGUOUS || attempt.getStatus() == PaymentStatus.CREATED) {
                    markAsFailed(attempt, "PAYMENT_NOT_FOUND");
                }
                return attempt;
            }

            String razorpayStatus = payment.get("status");
            attempt.setRazorpayPaymentId(payment.get("id"));

            // 3. Precise status mapping
            if ("captured".equalsIgnoreCase(razorpayStatus)) {
                PaymentStatus oldStatus = attempt.getStatus();
                attempt.setStatus(PaymentStatus.CAPTURED);
                attempt.setResolvedAt(LocalDateTime.now());

                if (oldStatus == PaymentStatus.CREATED) {
                    writeAuditLog(attempt, "RESOLVE_CREATED",
                            "Recovered a payment that was missing its acknowledgment.", "UPDATED_TO_CAPTURED",
                            AuditOutcome.RECOVERED_LOST_ACK);
                } else {
                    writeAuditLog(attempt, "STATUS_CHECK", "Payment was successfully captured by Razorpay.",
                            "UPDATED_TO_CAPTURED", AuditOutcome.SUCCESS);
                }
                return paymentAttemptRepository.save(attempt);

            } else if ("failed".equalsIgnoreCase(razorpayStatus)) {
                String errorReason = (payment.has("error_reason") && payment.get("error_reason") != null)
                        ? payment.get("error_reason").toString()
                        : "PAYMENT_FAILED";
                markAsFailed(attempt, errorReason);

            } else {
                paymentAttemptRepository.save(attempt);
            }

        } catch (Exception e) {
            log.error("Failed to fetch Razorpay status for attempt ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Transient error resolving payment ID: " + id, e);
        }

        return attempt;
    }

    private void markAsFailed(PaymentAttempt attempt, String reasonCode) {
        attempt.setStatus(PaymentStatus.FAILED);
        attempt.setFailureReasonCode(reasonCode);
        attempt.setResolvedAt(LocalDateTime.now());
        paymentAttemptRepository.save(attempt);

        String explanation = "Payment resolved as failed with reason code: " + reasonCode;

        try {
            explanation = chatClient.prompt()
                    .user(u -> u.text("Explain payment failure reason code: {reason_code}")
                            .param("reason_code", reasonCode))
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Failed to fetch AI explanation for reason code '{}': {}", reasonCode, e.getMessage());
        }

        writeAuditLog(attempt, "STATUS_CHECK", explanation, "RETRY_SCHEDULED_OR_FAILED", AuditOutcome.FAILED);
    }

    private void writeAuditLog(PaymentAttempt attempt, String decision, String reasoning, String actionTaken,
                               AuditOutcome outcome) {
        Optional<AuditLog> lastLog = auditLogRepository.getLastAudit(attempt.getRazorpayOrderId());

        int nextAttemptCount = lastLog
                .map(log -> log.getAttemptNumber() != null ? log.getAttemptNumber() + 1 : 1)
                .orElse(1);

        AuditLog logEntity = new AuditLog();
        logEntity.setFlowType(FlowType.PAYMENT_DEGRADATION);
        logEntity.setEntityId(attempt.getId());
        logEntity.setEntityType("payment_attempt");
        logEntity.setDecision(decision);
        logEntity.setReasoning(reasoning);
        logEntity.setActionTaken(actionTaken);
        logEntity.setOutcome(outcome);
        logEntity.setAttemptNumber(nextAttemptCount);
        logEntity.setCreatedAt(LocalDateTime.now());
        logEntity.setPaymentOrderId(attempt.getRazorpayOrderId());

        auditLogRepository.save(logEntity);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public String updatePaymentStatus(Map<String, Object> payload) {
        if (payload == null || !payload.containsKey("payload")) {
            log.warn("Invalid webhook payload received.");
            return "INVALID_PAYLOAD";
        }

        Map<String, Object> payloadData = (Map<String, Object>) payload.get("payload");
        Map<String, Object> paymentWrapper = (Map<String, Object>) payloadData.get("payment");
        Map<String, Object> entity = (Map<String, Object>) paymentWrapper.get("entity");
        Map<String, Object> notes = entity != null ? (Map<String, Object>) entity.get("notes") : null;

        String paymentId = entity != null ? (String) entity.get("id") : null;
        String orderId = entity != null ? (String) entity.get("order_id") : null;
        Object rawAmount = entity != null ? entity.get("amount") : null;
        String status = entity != null ? (String) entity.get("status") : null;

        if (notes != null && "true".equals(String.valueOf(notes.get("simulate_drop")))) {
            log.info("DEMO BYPASS: Webhook swallowed for testing. Order: {}", orderId);
            return status;
        }

        if (orderId == null) {
            log.warn("Webhook missing order_id for paymentId: {}", paymentId);
            return status;
        }

        PaymentAttempt paymentAttempt = paymentAttemptRepository.findByRazorpayOrderId(orderId);
        if (paymentAttempt == null) {
            log.warn("Received webhook for untracked orderId: {}", orderId);
            return status;
        }

        if ("captured".equalsIgnoreCase(status)) {
            paymentAttempt.setStatus(PaymentStatus.CAPTURED);
            paymentAttempt.setResolvedAt(LocalDateTime.now());
        } else if ("authorized".equalsIgnoreCase(status)) {
            paymentAttempt.setStatus(PaymentStatus.AUTHORIZED);
        } else if ("failed".equalsIgnoreCase(status)) {
            paymentAttempt.setStatus(PaymentStatus.FAILED);
        } else {
            paymentAttempt.setStatus(PaymentStatus.AMBIGUOUS);
        }

        paymentAttemptRepository.save(paymentAttempt);
        log.info("Webhook updated attempt ID {} to status {}", paymentAttempt.getId(), status);
        return status;
    }

    @Transactional(readOnly = true)
    public List<BankHealthSnapshot> getLatestBankHealth() {
        List<BankHealthSnapshot> allSnapshots = bankHealthSnapshotRepository.findAll();
        Map<String, BankHealthSnapshot> latestSnapshots = new HashMap<>();

        for (BankHealthSnapshot snapshot : allSnapshots) {
            BankHealthSnapshot existing = latestSnapshots.get(snapshot.getBankName());
            if (existing == null || snapshot.getId() > existing.getId()) {
                latestSnapshots.put(snapshot.getBankName(), snapshot);
            }
        }

        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        List<BankHealthSnapshot> adjustedSnapshots = new ArrayList<>();

        for (BankHealthSnapshot snapshot : latestSnapshots.values()) {
            if (snapshot.getWindowEnd() != null && snapshot.getWindowEnd().isBefore(oneMinuteAgo)) {
                BankHealthSnapshot healthy = new BankHealthSnapshot();
                healthy.setId(snapshot.getId());
                healthy.setBankName(snapshot.getBankName());
                healthy.setPaymentMethod(snapshot.getPaymentMethod());
                healthy.setWindowStart(snapshot.getWindowStart());
                healthy.setWindowEnd(snapshot.getWindowEnd());
                healthy.setTotalAttempts(0);
                healthy.setSuccessCount(0);
                healthy.setSuccessRate(BigDecimal.ONE);
                healthy.setBaselineSuccessRate(snapshot.getBaselineSuccessRate());
                healthy.setIsDegraded(false);
                healthy.setAiSummary(null);
                adjustedSnapshots.add(healthy);
            } else {
                adjustedSnapshots.add(snapshot);
            }
        }

        return adjustedSnapshots;
    }
}