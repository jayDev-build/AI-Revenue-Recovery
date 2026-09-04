package ai.revenue.recovery.service;

import ai.revenue.recovery.AiTools.SubscriptionTools;
import ai.revenue.recovery.config.AppClock;
import ai.revenue.recovery.entity.*;
import ai.revenue.recovery.entity.Responses.LlmExtractionResult;
import ai.revenue.recovery.entity.enums.*;
import ai.revenue.recovery.repository.AuditLogRepository;
import ai.revenue.recovery.repository.PromiseConfirmationStateRepository;
import ai.revenue.recovery.repository.PromiseToPayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PromiseValidationService {

    private static final Logger log = LoggerFactory.getLogger(PromiseValidationService.class);

    // Tolerance for amount validation: ±20%
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.20");
    // Minimum confidence score threshold
    private static final double MIN_CONFIDENCE = 0.7;

    private final PromiseToPayRepository promiseToPayRepository;
    private final PromiseConfirmationStateRepository confirmationStateRepository;
    private final AuditLogRepository auditLogRepository;
    private final SubscriptionTools subscriptionTools;

    public PromiseValidationService(PromiseToPayRepository promiseToPayRepository,
                                    PromiseConfirmationStateRepository confirmationStateRepository,
                                    AuditLogRepository auditLogRepository,
                                    SubscriptionTools subscriptionTools) {
        this.promiseToPayRepository = promiseToPayRepository;
        this.confirmationStateRepository = confirmationStateRepository;
        this.auditLogRepository = auditLogRepository;
        this.subscriptionTools = subscriptionTools;
    }

    /**
     * Deterministic validation pipeline for LLM-extracted promise data.
     * Returns the created PromiseToPay with either PENDING or NEEDS_HUMAN_REVIEW status.
     */
    @Transactional
    public PromiseToPay validateAndCreatePromise(Customer customer, LlmExtractionResult extraction,
                                                  String rawMessage, String phoneNumber) {
        List<String> validationFailures = new ArrayList<>();

        // 1. Parse and validate date
        LocalDateTime promisedDateTime = null;
        LocalDate promisedDate = null;
        try {
            try {
                promisedDateTime = LocalDateTime.parse(extraction.getExtractedDate());
            } catch (Exception ex) {
                promisedDateTime = LocalDate.parse(extraction.getExtractedDate()).atStartOfDay();
            }
            promisedDate = promisedDateTime.toLocalDate();

            if (!promisedDate.isAfter(LocalDate.now(AppClock.zone()))) {
                validationFailures.add("Extracted date '" + promisedDate + "' is not in the future");
            }
        } catch (Exception e) {
            validationFailures.add("Failed to parse extracted date: '" + extraction.getExtractedDate() + "'");
        }

        // 2. Validate amount (if provided) against related subscription
        BigDecimal extractedAmount = extraction.getExtractedAmount();
        Subscription linkedSub = findLinkedSubscription(customer);
        if (extractedAmount != null && linkedSub != null && linkedSub.getPlanAmount() != null) {
            BigDecimal planAmount = linkedSub.getPlanAmount();
            BigDecimal lowerBound = planAmount.multiply(BigDecimal.ONE.subtract(AMOUNT_TOLERANCE));
            BigDecimal upperBound = planAmount.multiply(BigDecimal.ONE.add(AMOUNT_TOLERANCE));

            if (extractedAmount.compareTo(lowerBound) < 0 || extractedAmount.compareTo(upperBound) > 0) {
                validationFailures.add("Extracted amount ₹" + extractedAmount +
                        " is outside ±20% of plan amount ₹" + planAmount);
            }
        }

        // 3. Check confidence score
        if (extraction.getConfidenceScore() < MIN_CONFIDENCE) {
            validationFailures.add("Confidence score " + extraction.getConfidenceScore() +
                    " is below threshold " + MIN_CONFIDENCE);
        }

        // 4. Find existing active promise or create a new one
        List<PromiseToPay> existingPromises = promiseToPayRepository.findByCustomerId(customer.getId());
        PromiseToPay promise = existingPromises.stream()
                .filter(p -> p.getStatus() == PromiseStatus.PENDING || p.getStatus() == PromiseStatus.NEEDS_HUMAN_REVIEW)
                .filter(p -> linkedSub == null || (linkedSub.getId().equals(p.getRelatedEntityId()) && "SUBSCRIPTION".equals(p.getRelatedEntityType())))
                .findFirst()
                .orElse(new PromiseToPay());

        promise.setCustomer(customer);
        promise.setRawMessage(rawMessage);
        promise.setExtractedPromiseDate(promisedDate);
        promise.setExtractedConfidence(BigDecimal.valueOf(extraction.getConfidenceScore()));
        promise.setExtractedAmount(extractedAmount);
        
        if (promise.getId() == null) {
            promise.setCreatedAt(AppClock.now());
        }

        if (linkedSub != null) {
            promise.setRelatedEntityType("SUBSCRIPTION");
            promise.setRelatedEntityId(linkedSub.getId());
        }

        if (validationFailures.isEmpty()) {
            // All validations passed — PENDING
            promise.setStatus(PromiseStatus.PENDING);
            promiseToPayRepository.save(promise);

            // Delay subscription charge
            if (linkedSub != null && promisedDateTime != null) {
                try {
                    subscriptionTools.delaySubscriptionCharge(linkedSub.getId(), promisedDateTime.toString());
                } catch (Exception e) {
                    log.warn("Failed to delay subscription charge: {}", e.getMessage());
                }
            }

            writeGuardrailAudit(promise,
                    "All validations passed. Date: " + promisedDate + ", Confidence: " +
                            extraction.getConfidenceScore(),
                    "PROMISE_PENDING", AuditOutcome.SUCCESS);

            log.info("[GUARDRAIL] Promise ID {} PENDING for customer ID {}",
                    promise.getId(), customer.getId());
        } else {
            // Validation failed — NEEDS_HUMAN_REVIEW + create confirmation state
            promise.setStatus(PromiseStatus.NEEDS_HUMAN_REVIEW);
            promiseToPayRepository.save(promise);

            // Create or update confirmation state for this phone number
            String normalizedPhone = normalizePhoneNumber(phoneNumber);
            confirmationStateRepository.findByPhoneNumber(normalizedPhone)
                    .ifPresent(existing -> confirmationStateRepository.delete(existing));

            PromiseConfirmationState state = PromiseConfirmationState.builder()
                    .phoneNumber(normalizedPhone)
                    .pendingPromiseId(promise.getId())
                    .status("AWAITING_PROMISE_CONFIRMATION")
                    .createdAt(AppClock.now())
                    .build();
            confirmationStateRepository.save(state);

            String failureReasons = String.join("; ", validationFailures);
            writeGuardrailAudit(promise,
                    "Validation failed: " + failureReasons,
                    "AWAITING_HUMAN_REVIEW", AuditOutcome.PENDING);

            log.info("[GUARDRAIL] Promise ID {} marked NEEDS_HUMAN_REVIEW for customer ID {}. Reasons: {}",
                    promise.getId(), customer.getId(), failureReasons);
        }

        return promise;
    }

    /**
     * Confirms a pending promise when the customer replies with an affirmative.
     */
    @Transactional
    public void confirmPendingPromise(String phoneNumber) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        PromiseConfirmationState state = confirmationStateRepository.findByPhoneNumber(normalizedPhone)
                .orElseThrow(() -> new RuntimeException("No pending confirmation for phone: " + phoneNumber));

        PromiseToPay promise = promiseToPayRepository.findById(state.getPendingPromiseId())
                .orElseThrow(() -> new RuntimeException("Promise not found: " + state.getPendingPromiseId()));

        promise.setStatus(PromiseStatus.PENDING);
        promise.setResolvedAt(AppClock.now());
        promiseToPayRepository.save(promise);

        // Delay subscription charge if linked
        if ("SUBSCRIPTION".equals(promise.getRelatedEntityType()) && promise.getRelatedEntityId() != null
                && promise.getExtractedPromiseDate() != null) {
            try {
                subscriptionTools.delaySubscriptionCharge(
                        promise.getRelatedEntityId(),
                        promise.getExtractedPromiseDate().atStartOfDay().toString());
            } catch (Exception e) {
                log.warn("Failed to delay subscription charge on confirmation: {}", e.getMessage());
            }
        }

        // Clear the confirmation state
        confirmationStateRepository.deleteByPhoneNumber(normalizedPhone);

        writeGuardrailAudit(promise,
                "Customer confirmed via WhatsApp reply. Promise ID: " + promise.getId(),
                "HUMAN_CONFIRMED", AuditOutcome.SUCCESS);

        log.info("[GUARDRAIL] Promise ID {} PENDING (confirmed by customer via WhatsApp).", promise.getId());
    }

    /**
     * Rejects a pending promise when the customer replies with a negative.
     */
    @Transactional
    public void rejectPendingPromise(String phoneNumber) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        PromiseConfirmationState state = confirmationStateRepository.findByPhoneNumber(normalizedPhone)
                .orElse(null);

        if (state != null) {
            PromiseToPay promise = promiseToPayRepository.findById(state.getPendingPromiseId())
                    .orElse(null);

            if (promise != null) {
                writeGuardrailAudit(promise,
                        "Customer rejected promise via WhatsApp reply. Promise ID: " + promise.getId(),
                        "HUMAN_REJECTED", AuditOutcome.FAILED);
            }

            confirmationStateRepository.deleteByPhoneNumber(normalizedPhone);
            log.info("[GUARDRAIL] Promise cancelled by customer for phone: {}", phoneNumber);
        }
    }

    /**
     * Checks if a phone number has an active (non-expired) confirmation state.
     */
    public PromiseConfirmationState getActiveConfirmationState(String phoneNumber) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        return confirmationStateRepository.findByPhoneNumber(normalizedPhone)
                .filter(state -> state.getCreatedAt() != null &&
                        state.getCreatedAt().isAfter(AppClock.now().minusHours(24)))
                .orElse(null);
    }

    /**
     * Checks if a phone number has an expired confirmation state, and deletes it.
     */
    public boolean deleteExpiredConfirmationState(String phoneNumber) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        return confirmationStateRepository.findByPhoneNumber(normalizedPhone)
                .filter(state -> state.getCreatedAt() == null ||
                        state.getCreatedAt().isBefore(AppClock.now().minusHours(24)))
                .map(state -> {
                    confirmationStateRepository.delete(state);
                    log.info("[GUARDRAIL] Deleted expired confirmation state for phone: {}", phoneNumber);
                    return true;
                })
                .orElse(false);
    }

    private Subscription findLinkedSubscription(Customer customer) {
        if (customer == null || customer.getSubscriptions() == null) {
            return null;
        }
        return customer.getSubscriptions().stream()
                .filter(s -> SubscriptionStatus.PAST_DUE.equals(s.getStatus()) ||
                        SubscriptionStatus.CANCELLED.equals(s.getStatus()))
                .findFirst()
                .orElse(customer.getSubscriptions().isEmpty() ? null : customer.getSubscriptions().get(0));
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return "";
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        return digits.length() >= 10 ? digits.substring(digits.length() - 10) : digits;
    }

    private void writeGuardrailAudit(PromiseToPay promise, String reasoning, String actionTaken,
                                      AuditOutcome outcome) {
        AuditLog logEntry = AuditLog.builder()
                .flowType(FlowType.WHATSAPP_GUARDRAIL)
                .entityId(promise.getId())
                .entityType("promise_to_pay")
                .decision("GUARDRAIL_VALIDATION")
                .reasoning(reasoning)
                .actionTaken(actionTaken)
                .outcome(outcome)
                .attemptNumber(1)
                .createdAt(AppClock.now())
                .build();

        auditLogRepository.save(logEntry);
    }
}
