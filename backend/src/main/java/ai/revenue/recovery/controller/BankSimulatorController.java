package ai.revenue.recovery.controller;

import ai.revenue.recovery.entity.PaymentAttempt;
import ai.revenue.recovery.entity.Requests.BankCallbackPayload;
import ai.revenue.recovery.entity.enums.PaymentStatus;
import ai.revenue.recovery.repository.PaymentAttemptRepository;
import ai.revenue.recovery.service.BankSimulatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bank")
@RequiredArgsConstructor
public class BankSimulatorController {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final BankSimulatorService bankSimulatorService;

    // Fetch pending payment attempts directly
    @GetMapping("/pending-requests")
    public ResponseEntity<List<PaymentAttempt>> getPendingRequests() {
        List<PaymentAttempt> allPending = paymentAttemptRepository.findByStatus(PaymentStatus.PENDING);
        
        java.util.Map<String, PaymentAttempt> latestAttempts = new java.util.HashMap<>();

        // Loop through and only keep the newest attempt per subscription
        for (PaymentAttempt attempt : allPending) {
            String key = attempt.getSubscription() != null ? 
                "SUB_" + attempt.getSubscription().getId() : 
                "ATT_" + attempt.getId();

            PaymentAttempt existing = latestAttempts.get(key);
            if (existing == null || attempt.getInitiatedAt().isAfter(existing.getInitiatedAt())) {
                latestAttempts.put(key, attempt);
            }
        }

        // Sort them by newest first
        List<PaymentAttempt> result = new java.util.ArrayList<>(latestAttempts.values());
        result.sort(java.util.Comparator.comparing(PaymentAttempt::getInitiatedAt).reversed());

        return ResponseEntity.ok(result);
    }

    // Process bank callback
    @PostMapping("/callback")
    public ResponseEntity<String> handleBankCallback(@RequestBody BankCallbackPayload payload) {
        bankSimulatorService.processBankCallback(payload);
        return ResponseEntity.ok("Processed callback for Order: " + payload.getRazorpayOrderId());
    }
}