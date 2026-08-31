package ai.revenue.recovery.AiTools;

import ai.revenue.recovery.entity.Subscription;
import ai.revenue.recovery.repository.SubscriptionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class SubscriptionTools {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionTools(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Tool(description = "tool to delay the next Charge Date Time of subscription")
    public void delaySubscriptionCharge(@ToolParam(description = "Id of the Subscription") Long subscriptionId,
            @ToolParam(description = "Number of days to delay the payment (e.g. 1 for tomorrow, 7 for next week)") Integer delayDays) {
        try {
            Subscription subscription = subscriptionRepository.findById(subscriptionId).get();
            java.time.LocalDateTime dateTime = java.time.LocalDateTime.now().plusDays(delayDays);
            subscription.setNextChargeDate(dateTime);
            subscriptionRepository.save(subscription);
            System.out.println("Tool successfully delayed subscription " + subscriptionId + " by " + delayDays + " days to " + dateTime);
        } catch (Exception e) {
            System.err.println("Failed to execute tool delaySubscriptionCharge: " + e.getMessage());
        }
    }
}
