package ai.revenue.recovery.job;

import ai.revenue.recovery.entity.Customer;
import ai.revenue.recovery.entity.PromiseToPay;
import ai.revenue.recovery.entity.enums.PromiseStatus;
import ai.revenue.recovery.repository.CustomerRepository;
import ai.revenue.recovery.repository.PromiseToPayRepository;
import ai.revenue.recovery.Whatsapp.WhatsAppNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class PromiseToPayScheduler {

    private static final Logger log = LoggerFactory.getLogger(PromiseToPayScheduler.class);

    private final PromiseToPayRepository promiseToPayRepository;
    private final CustomerRepository customerRepository;
    private final WhatsAppNotificationService notificationService;

    public PromiseToPayScheduler(PromiseToPayRepository promiseToPayRepository,
                                 CustomerRepository customerRepository,
                                 WhatsAppNotificationService notificationService) {
        this.promiseToPayRepository = promiseToPayRepository;
        this.customerRepository = customerRepository;
        this.notificationService = notificationService;
    }

    // Run every day at 10 AM (adjust cron as needed)
    @Scheduled(cron = "0 0 10 * * *")
    public void processPromisesToPay() {
        log.info("Starting scheduled job: processPromisesToPay");
        
        List<PromiseToPay> pendingPromises = promiseToPayRepository.findByStatus(PromiseStatus.PENDING);
        LocalDate today = LocalDate.now();

        for (PromiseToPay promise : pendingPromises) {
            LocalDate promiseDate = promise.getExtractedPromiseDate();
            if (promiseDate == null) continue;

            Customer customer = promise.getCustomer();

            if (promiseDate.isEqual(today)) {
                // Send reminder on the day of the promise
                if (customer.getPhoneNumber() != null) {
                    String reason = promise.getRelatedEntityType() != null ? promise.getRelatedEntityType() : "your pending balance";
                    notificationService.sendPromiseToPayReminder(
                        customer.getPhoneNumber(),
                        customer.getName() != null ? customer.getName() : "Customer",
                        reason,
                        promiseDate.toString()
                    );
                    log.info("Sent Promise to Pay reminder to customer: {}", customer.getId());
                }
            } else if (promiseDate.isBefore(today)) {
                // Promise broken
                promise.setStatus(PromiseStatus.BROKEN);
                promiseToPayRepository.save(promise);

                // Increment broken promises count
                if (customer.getBrokenPromisesCount() == null) {
                    customer.setBrokenPromisesCount(0);
                }
                customer.setBrokenPromisesCount(customer.getBrokenPromisesCount() + 1);
                customerRepository.save(customer);
                log.info("Marked promise {} as BROKEN for customer: {}", promise.getId(), customer.getId());
            }
        }
    }
}
