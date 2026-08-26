package ai.revenue.recovery.controller;

import ai.revenue.recovery.entity.BankHealthSnapshot;
import ai.revenue.recovery.entity.PaymentAttempt;
import ai.revenue.recovery.entity.enums.PaymentMethod;
import ai.revenue.recovery.repository.BankHealthSnapshotRepository;
import ai.revenue.recovery.service.DemoDataSeedingService;
import ai.revenue.recovery.service.PaymentDegradationService;
import com.google.gson.JsonObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("")
public class PaymentController {

    private final PaymentDegradationService paymentService;
    private final DemoDataSeedingService seedingService;
    private final BankHealthSnapshotRepository bankHealthSnapshotRepository;

    public PaymentController(PaymentDegradationService paymentService,
            DemoDataSeedingService seedingService,
            BankHealthSnapshotRepository bankHealthSnapshotRepository) {
        this.paymentService = paymentService;
        this.seedingService = seedingService;
        this.bankHealthSnapshotRepository = bankHealthSnapshotRepository;
    }

    @PostMapping("/seed")
    public ResponseEntity<String> seedData() {
        seedingService.seedDegradationData();
        return ResponseEntity.ok("Demo degradation data seeded successfully.");
    }

    @PostMapping("/payments/initiate")
    public ResponseEntity<PaymentAttempt> initiatePayment(@RequestBody Map<String, Object> request) {
        Long customerId = Long.valueOf(request.get("customerId").toString());
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        PaymentMethod method = PaymentMethod.valueOf(request.get("method").toString());
        return ResponseEntity.ok(paymentService.initiatePayment(customerId, amount, method));
    }

    @GetMapping("/payments/{id}/status")
    public ResponseEntity<PaymentAttempt> getPaymentStatus(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentStatus(id));
    }

    @PostMapping("/payments/{id}/resolve")
    public ResponseEntity<PaymentAttempt> resolvePayment(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.resolvePayment(id));
    }

    @PostMapping("/razorpay/webhook")
    public ResponseEntity<String> resolvePayment(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(paymentService.updatePaymentStatus(payload));
    }

    @GetMapping("/bank-health")
    public ResponseEntity<List<BankHealthSnapshot>> getBankHealth() {
        return ResponseEntity.ok(bankHealthSnapshotRepository.findAll());
    }
}
