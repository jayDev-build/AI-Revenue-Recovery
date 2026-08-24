package ai.revenue.recovery.service;

import ai.revenue.recovery.entity.Customer;
import ai.revenue.recovery.entity.PaymentAttempt;
import ai.revenue.recovery.entity.enums.PaymentMethod;
import ai.revenue.recovery.entity.enums.PaymentStatus;
import ai.revenue.recovery.repository.CustomerRepository;
import ai.revenue.recovery.repository.PaymentAttemptRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DemoDataSeedingService {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CustomerRepository customerRepository;

    public DemoDataSeedingService(PaymentAttemptRepository paymentAttemptRepository, CustomerRepository customerRepository) {
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.customerRepository = customerRepository;
    }

    public void seedDegradationData() {
        // Ensure we have at least one customer
        Customer customer = customerRepository.findById(1L).orElseGet(() -> {
            Customer c = new Customer();
            c.setName("Demo User");
            c.setEmail("demo@example.com");
            c.setPhoneNumber("+919999999999");
            c.setCreatedAt(LocalDateTime.now());
            return customerRepository.save(c);
        });

        List<PaymentAttempt> attempts = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // Seed 100 successful payments for Bank X
        for (int i = 0; i < 100; i++) {
            PaymentAttempt attempt = new PaymentAttempt();
            attempt.setCustomer(customer);
            attempt.setAmount(new BigDecimal("1000.00"));
            attempt.setPaymentMethod(PaymentMethod.UPI);
            attempt.setCustomerBank("Bank X");
            attempt.setStatus(PaymentStatus.CAPTURED);
            attempt.setInitiatedAt(now.minusSeconds(30)); // 30 seconds ago
            attempts.add(attempt);
        }

        // Seed 20 failed/ambiguous payments for Bank X to cause a sudden dip in success rate
        for (int i = 0; i < 20; i++) {
            PaymentAttempt attempt = new PaymentAttempt();
            attempt.setCustomer(customer);
            attempt.setAmount(new BigDecimal("1000.00"));
            attempt.setPaymentMethod(PaymentMethod.UPI);
            attempt.setCustomerBank("Bank X");
            // 15% random chance it's ambiguous, otherwise failed
            attempt.setStatus(Math.random() < 0.15 ? PaymentStatus.AMBIGUOUS : PaymentStatus.FAILED);
            attempt.setRazorpayOrderId("demo_order_" + System.currentTimeMillis() + i);
            attempt.setFailureReasonCode("TIMEOUT");
            attempt.setInitiatedAt(now.minusSeconds(10)); // 10 seconds ago
            attempts.add(attempt);
        }

        paymentAttemptRepository.saveAll(attempts);
    }
}
