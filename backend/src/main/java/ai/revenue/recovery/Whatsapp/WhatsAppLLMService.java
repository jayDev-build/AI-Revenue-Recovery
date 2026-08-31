package ai.revenue.recovery.Whatsapp;

import ai.revenue.recovery.entity.Customer;
import ai.revenue.recovery.entity.PaymentAttempt;
import ai.revenue.recovery.entity.Subscription;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WhatsAppLLMService {

    private final WhatsAppNotificationService notificationService;
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public WhatsAppLLMService(WhatsAppNotificationService notificationService, ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.notificationService = notificationService;
        this.chatClient = chatClientBuilder.defaultSystem(
            "You are a helpful customer support AI. Generate very concise, friendly 1-2 sentence explanations " +
            "for transaction failures to be inserted into WhatsApp message templates. Keep it extremely brief and do not use markdown."
        ).build();
        this.chatMemory = chatMemory;
    }

    public void sendPaymentFailedTemplate(Customer customer, PaymentAttempt attempt, String technicalReason) {
        if (customer == null || customer.getPhoneNumber() == null) return;
        
        String explanation = chatClient.prompt()
            .user(u -> u.text("Generate a brief explanation for the customer. Payment amount: {amount}, Reason: {reason}.")
                    .param("amount", attempt.getAmount())
                    .param("reason", technicalReason))
            .call()
            .content();

        // Template: recovery_payment_degraded_v1
        // Variables: {{1}} Name, {{2}} Amount, {{3}} Order #, {{4}} Bank/Method, {{5}} LLM Explanation
        List<String> values = List.of(
                customer.getName() != null ? customer.getName() : "Customer",
                attempt.getAmount().toString(),
                attempt.getRazorpayOrderId() != null ? attempt.getRazorpayOrderId() : "N/A",
                attempt.getCustomerBank() != null ? attempt.getCustomerBank() : "your bank",
                explanation != null ? explanation : "We encountered an issue processing your payment."
        );

        notificationService.sendTemplateNotification(customer.getPhoneNumber(), "recovery_payment_degraded_v1", "en", values);
        
        // Save this outgoing notification to Chat Memory (DB) so we have context for future incoming replies
        String sentMessage = "System Notification (Payment Failed): " + (explanation != null ? explanation : "Payment failed.");
        chatMemory.add(customer.getPhoneNumber(), new AssistantMessage(sentMessage));
    }

    public void sendSubscriptionFailedTemplate(Customer customer, Subscription subscription, String technicalReason) {
        if (customer == null || customer.getPhoneNumber() == null) return;

        String explanation = chatClient.prompt()
            .user(u -> u.text("Generate a brief explanation for a failed subscription renewal. Plan: {plan}, Reason: {reason}.")
                    .param("plan", subscription.getDescription())
                    .param("reason", technicalReason))
            .call()
            .content();

        // Template: sub_failed_interactive_v1
        // Variables: {{1}} Name, {{2}} Plan description, {{3}} Amount, {{4}} LLM Generated reason
        List<String> values = List.of(
                customer.getName() != null ? customer.getName() : "Customer",
                subscription.getDescription() != null ? subscription.getDescription() : "Standard Plan",
                subscription.getPlanAmount().toString(),
                explanation != null ? explanation : "There was a problem renewing your subscription."
        );

        notificationService.sendTemplateNotification(customer.getPhoneNumber(), "sub_failed_interactive_v1", "en", values);
        
        // Save this outgoing notification to Chat Memory (DB) so we have context for future incoming replies
        String sentMessage = "System Notification (Subscription Failed): " + (explanation != null ? explanation : "Subscription failed.");
        chatMemory.add(customer.getPhoneNumber(), new AssistantMessage(sentMessage));
    }
}
