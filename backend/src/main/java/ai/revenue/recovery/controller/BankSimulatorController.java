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
        return ResponseEntity.ok(paymentAttemptRepository.findByStatus(PaymentStatus.PENDING));
    }

    // Process bank callback
    @PostMapping("/callback")
    public ResponseEntity<String> handleBankCallback(@RequestBody BankCallbackPayload payload) {
        bankSimulatorService.processBankCallback(payload);
        return ResponseEntity.ok("Processed callback for Order: " + payload.getRazorpayOrderId());
    }
}