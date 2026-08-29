package ai.revenue.recovery.job;

import ai.revenue.recovery.service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionScheduler.class);

    private final SubscriptionService subscriptionService;

    public SubscriptionScheduler(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * Scans and processes due subscriptions based on the cron expression.
     * Default: Runs every 1 minute.
     */
        @Scheduled(cron = "${subscription.renewal.cron:*/30 * * * * *}")
        public void processDueSubscriptionPayments() {
        log.info("Cron Job Started: Scanning for due subscription payments...");
        try {
            subscriptionService.processDueSubscriptions();
            log.info("Cron Job Finished: Subscription processing completed.");
        } catch (Exception e) {
            log.error("Cron Job Error: Failed during subscription execution", e);
        }
    }
}