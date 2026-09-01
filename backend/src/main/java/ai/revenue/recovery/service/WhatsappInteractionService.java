package ai.revenue.recovery.service;

import ai.revenue.recovery.AiTools.SubscriptionTools;
import ai.revenue.recovery.entity.Customer;
import ai.revenue.recovery.entity.PromiseToPay;
import ai.revenue.recovery.entity.Subscription;
import ai.revenue.recovery.entity.enums.PromiseStatus;
import ai.revenue.recovery.repository.CustomerRepository;
import ai.revenue.recovery.repository.PromiseToPayRepository;
import ai.revenue.recovery.Whatsapp.WhatsAppNotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import java.util.List;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class WhatsappInteractionService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappInteractionService.class);

    private final ChatClient chatClient;
    private final CustomerRepository customerRepository;
    private final PromiseToPayRepository promiseToPayRepository;
    private final WhatsAppNotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final ChatMemory chatMemory;
    private final SubscriptionTools subscriptionTools;

    public WhatsappInteractionService(ChatClient.Builder chatClientBuilder,
                                      CustomerRepository customerRepository,
                                      PromiseToPayRepository promiseToPayRepository,
                                      WhatsAppNotificationService notificationService,
                                      SubscriptionTools subscriptionTools,
                                      ChatMemory chatMemory) {
        this.chatClient = chatClientBuilder.defaultSystem(
            "You are an AI assistant analyzing a conversation with a customer regarding failed payments or subscriptions.\n" +
            "Based on the conversation history, extract the customer's intent from their latest message.\n" +
            "IMPORTANT: If the user promises to pay on a future date or specific time, YOU MUST use the provided tool to delay the subscription charge by passing the exact date and time they requested in ISO-8601 format (e.g. YYYY-MM-DDTHH:MM:SS).\n" +
            "IMPORTANT: If the user says they need more time, your reply should politely ask them how many days they need or what specific date they can pay on.\n" +
            "After you have used any necessary tools, your FINAL text response must be ONLY a raw JSON object with the following structure (no markdown):\n" +
            "{\n" +
            "  \"intent\": \"PROMISE_TO_PAY\" | \"ALREADY_PAID\" | \"NEED_MORE_TIME\" | \"CANCEL_SUBSCRIPTION\" | \"OTHER\",\n" +
            "  \"date\": \"YYYY-MM-DDTHH:MM:SS\" (if they mention a payment date or time, calculate the exact future date and time based on CRITICAL CONTEXT, otherwise null),\n" +
            "  \"confidence\": 0.95,\n" +
            "  \"reply\": \"A short, friendly 1-sentence reply to send back to the user.\"\n" +
            "}"
        ).build();
        this.customerRepository = customerRepository;
        this.promiseToPayRepository = promiseToPayRepository;
        this.notificationService = notificationService;
        this.chatMemory = chatMemory;
        this.subscriptionTools = subscriptionTools;
        this.objectMapper = new ObjectMapper();
    }

    @org.springframework.transaction.annotation.Transactional
    public void processIncomingMessage(String phoneNumber, String rawMessage) {
        log.info("Processing incoming WhatsApp message from {}", phoneNumber);

        Customer customer = findCustomerByPhone(phoneNumber);
        if (customer == null) {
            log.warn("No customer found for phone number: {}", phoneNumber);
            return;
        }

        try {
            // Fetch DB conversation history directly from auto-configured ChatMemory
            List<Message> historyMessages = new java.util.ArrayList<>(chatMemory.get(phoneNumber));
            
            String contextStr = "[CRITICAL CONTEXT: The current server date and time is " + LocalDateTime.now().toString() + "]\n\nUser: ";
            Message newUserMessage = new UserMessage(contextStr + rawMessage);
            historyMessages.add(newUserMessage);

            String jsonResponse = chatClient.prompt()
                    .messages(historyMessages)
                    .tools(subscriptionTools)
                    .call()
                    .content();

            if (jsonResponse != null) {
                jsonResponse = jsonResponse.trim();
                if (jsonResponse.startsWith("```json")) jsonResponse = jsonResponse.substring(7);
                else if (jsonResponse.startsWith("```")) jsonResponse = jsonResponse.substring(3);
                if (jsonResponse.endsWith("```")) jsonResponse = jsonResponse.substring(0, jsonResponse.length() - 3);
                jsonResponse = jsonResponse.trim();
            }

            JsonNode responseNode = objectMapper.readTree(jsonResponse);
            
            String intent = responseNode.path("intent").asText();
            String dateStr = responseNode.path("date").asText(null);
            double confidence = responseNode.path("confidence").asDouble(0.0);
            String reply = responseNode.path("reply").asText("Thank you for your message, we have noted it.");

            if ("PROMISE_TO_PAY".equalsIgnoreCase(intent) && dateStr != null && !"null".equalsIgnoreCase(dateStr)) {
                PromiseToPay promise = new PromiseToPay();
                promise.setCustomer(customer);
                promise.setRawMessage(rawMessage);

                Subscription linkedSubToUpdate = null;
                // Find a past_due or cancelled subscription for the customer to link
                List<Subscription> subs = customer.getSubscriptions();
                if (subs != null && !subs.isEmpty()) {
                    Subscription linkedSub = subs.stream()
                        .filter(s -> ai.revenue.recovery.entity.enums.SubscriptionStatus.PAST_DUE.equals(s.getStatus()) || 
                                     ai.revenue.recovery.entity.enums.SubscriptionStatus.CANCELLED.equals(s.getStatus()))
                        .findFirst()
                        .orElse(subs.get(0));
                    promise.setRelatedEntityType("SUBSCRIPTION");
                    promise.setRelatedEntityId(linkedSub.getId());
                    linkedSubToUpdate = linkedSub;
                }

                try {
                    // Try to parse as full ISO-8601 Date Time
                    LocalDateTime promisedDateTime;
                    try {
                        promisedDateTime = LocalDateTime.parse(dateStr);
                    } catch (Exception ex) {
                        // Fallback to LocalDate if time is missing
                        promisedDateTime = LocalDate.parse(dateStr).atStartOfDay();
                    }
                    promise.setExtractedPromiseDate(promisedDateTime.toLocalDate());

                    if (linkedSubToUpdate != null) {
                        // Immediately push the nextChargeDate so Bank Simulator stops retrying
                        linkedSubToUpdate.setNextChargeDate(promisedDateTime);
                        // We can rely on JPA cascade if Customer is saved, but let's be safe
                        // Actually, we don't have subscriptionRepository here. 
                        // But since we didn't inject it, let's use the subscriptionTools which has the repository!
                        subscriptionTools.delaySubscriptionCharge(linkedSubToUpdate.getId(), promisedDateTime.toString());
                    }

                } catch (Exception e) {
                    log.warn("Failed to parse date: {}", dateStr);
                }
                promise.setExtractedConfidence(BigDecimal.valueOf(confidence));
                promise.setStatus(PromiseStatus.PENDING);
                promise.setCreatedAt(LocalDateTime.now());
                promiseToPayRepository.save(promise);
                log.info("Saved PromiseToPay for customer ID {}", customer.getId());
            } else if ("ALREADY_PAID".equalsIgnoreCase(intent)) {
                List<PromiseToPay> pendingPromises = promiseToPayRepository.findByCustomerId(customer.getId())
                        .stream().filter(p -> p.getStatus() == PromiseStatus.PENDING).toList();
                
                for (PromiseToPay p : pendingPromises) {
                    p.setStatus(PromiseStatus.KEPT);
                    p.setResolvedAt(LocalDateTime.now());
                    promiseToPayRepository.save(p);
                }
                log.info("Marked {} pending promises as KEPT for customer ID {} due to ALREADY_PAID intent.", pendingPromises.size(), customer.getId());
            }

            // Save AI reply and User message to memory
            chatMemory.add(phoneNumber, List.of(new UserMessage(rawMessage), new AssistantMessage(reply)));

            // Send interactive reply within 24-hour window
            notificationService.sendTextMessage(phoneNumber, reply);

        } catch (Exception e) {
            log.error("Failed to process incoming WhatsApp message: {}", e.getMessage());
        }
    }

    private Customer findCustomerByPhone(String phoneNumber) {
        String last10 = phoneNumber.length() >= 10 ? phoneNumber.substring(phoneNumber.length() - 10) : phoneNumber;
        
        for (Customer c : customerRepository.findAll()) {
            if (c.getPhoneNumber() != null) {
                String cLast10 = c.getPhoneNumber().length() >= 10 ? 
                                 c.getPhoneNumber().substring(c.getPhoneNumber().length() - 10) : c.getPhoneNumber();
                if (cLast10.equals(last10)) {
                    return c;
                }
            }
        }
        return null;
    }
}
