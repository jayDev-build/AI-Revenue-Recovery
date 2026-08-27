package ai.revenue.recovery.controller;

import ai.revenue.recovery.entity.AuditLog;
import ai.revenue.recovery.entity.BankHealthSnapshot;
import ai.revenue.recovery.entity.PaymentAttempt;
import ai.revenue.recovery.entity.enums.PaymentMethod;
import ai.revenue.recovery.repository.AuditLogRepository;
import ai.revenue.recovery.repository.BankHealthSnapshotRepository;
import ai.revenue.recovery.service.DemoDataSeedingService;
import ai.revenue.recovery.service.PaymentDegradationService;
import com.google.gson.JsonObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("")
public class PaymentController {

    private final PaymentDegradationService paymentService;
    private final DemoDataSeedingService seedingService;
    private final BankHealthSnapshotRepository bankHealthSnapshotRepository;
    private final AuditLogRepository auditLogRepository;

    public PaymentController(PaymentDegradationService paymentService,
            DemoDataSeedingService seedingService,
            BankHealthSnapshotRepository bankHealthSnapshotRepository,
            AuditLogRepository auditLogRepository) {
        this.paymentService = paymentService;
        this.seedingService = seedingService;
        this.bankHealthSnapshotRepository = bankHealthSnapshotRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @PostMapping("/seed")
    public ResponseEntity<String> seedData() {
        seedingService.seedDegradationData();
        return ResponseEntity.ok("Demo degradation data seeded successfully.");
    }

    @PostMapping("/demo/seed-failures")
    public ResponseEntity<String> seedFailures(@RequestBody Map<String, Object> request) {
        String bankName = request.get("bankName").toString();
        int failureCount = Integer.parseInt(request.get("failureCount").toString());
        seedingService.seedBankFailures(bankName, failureCount);
        return ResponseEntity.ok("Successfully seeded " + failureCount + " failures for " + bankName);
    }

    @PostMapping("/payments/initiate")
    public ResponseEntity<PaymentAttempt> initiatePayment(@RequestBody Map<String, Object> request) {
        Long customerId = Long.valueOf(request.get("customerId").toString());
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        PaymentMethod method = PaymentMethod.valueOf(request.get("method").toString());
        String bankName = request.containsKey("bankName") ? request.get("bankName").toString() : null;
        boolean simulateDrop = request.containsKey("simulateDrop") && Boolean.parseBoolean(request.get("simulateDrop").toString());
        return ResponseEntity.ok(paymentService.initiatePayment(customerId, amount, method, bankName, simulateDrop));
    }

    @GetMapping("/api/audit-logs/recent")
    public ResponseEntity<List<AuditLog>> getRecentAuditLogsApi() {
        return ResponseEntity.ok(auditLogRepository.findTop5ByOrderByCreatedAtDesc());
    }

    @GetMapping("/audit-logs/recent")
    public ResponseEntity<List<AuditLog>> getRecentAuditLogs() {
        return ResponseEntity.ok(auditLogRepository.findTop5ByOrderByCreatedAtDesc());
    }

    @GetMapping("/api/payments/latest")
    public ResponseEntity<PaymentAttempt> getLatestPaymentApi() {
        return ResponseEntity.ok(paymentService.getLatestPayment());
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

    @GetMapping("/api/bank-health/latest")
    public ResponseEntity<List<BankHealthSnapshot>> getLatestBankHealthApi() {
        List<BankHealthSnapshot> allSnapshots = bankHealthSnapshotRepository.findAll();
        Map<String, BankHealthSnapshot> latestSnapshots = new java.util.HashMap<>();
        for (BankHealthSnapshot snapshot : allSnapshots) {
            BankHealthSnapshot existing = latestSnapshots.get(snapshot.getBankName());
            if (existing == null || snapshot.getId() > existing.getId()) {
                latestSnapshots.put(snapshot.getBankName(), snapshot);
            }
        }
        java.time.LocalDateTime oneMinuteAgo = java.time.LocalDateTime.now().minusMinutes(1);
        List<BankHealthSnapshot> adjustedSnapshots = new java.util.ArrayList<>();
        
        for (BankHealthSnapshot snapshot : latestSnapshots.values()) {
            if (snapshot.getWindowEnd() != null && snapshot.getWindowEnd().isBefore(oneMinuteAgo)) {
                // If there has been no transactions in the last minute, reset to 100% health
                BankHealthSnapshot healthy = new BankHealthSnapshot();
                healthy.setId(snapshot.getId());
                healthy.setBankName(snapshot.getBankName());
                healthy.setPaymentMethod(snapshot.getPaymentMethod());
                healthy.setWindowStart(snapshot.getWindowStart());
                healthy.setWindowEnd(snapshot.getWindowEnd());
                healthy.setTotalAttempts(0);
                healthy.setSuccessCount(0);
                healthy.setSuccessRate(java.math.BigDecimal.ONE);
                healthy.setBaselineSuccessRate(snapshot.getBaselineSuccessRate());
                healthy.setIsDegraded(false);
                healthy.setAiSummary(null);
                adjustedSnapshots.add(healthy);
            } else {
                adjustedSnapshots.add(snapshot);
            }
        }

        return ResponseEntity.ok(adjustedSnapshots);
    }

    @GetMapping("/bank-health")
    public ResponseEntity<List<BankHealthSnapshot>> getBankHealth() {
        return ResponseEntity.ok(bankHealthSnapshotRepository.findAll());
    }
}
