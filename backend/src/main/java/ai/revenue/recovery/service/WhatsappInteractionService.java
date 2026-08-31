package ai.revenue.recovery.service;

import ai.revenue.recovery.entity.Customer;
import ai.revenue.recovery.entity.PromiseToPay;
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

    public WhatsappInteractionService(ChatClient.Builder chatClientBuilder,
                                      CustomerRepository customerRepository,
                                      PromiseToPayRepository promiseToPayRepository,
                                      WhatsAppNotificationService notificationService,
                                      ChatMemory chatMemory) {
        this.chatClient = chatClientBuilder.defaultSystem(
            "You are an AI assistant analyzing a conversation with a customer regarding failed payments or subscriptions.\n" +
            "Based on the conversation history, extract the customer's intent from their latest message.\n" +
            "Return ONLY a raw JSON object with the following structure (no markdown, no backticks):\n" +
            "{\n" +
            "  \"intent\": \"PROMISE_TO_PAY\" | \"CANCEL_SUBSCRIPTION\" | \"OTHER\",\n" +
            "  \"date\": \"YYYY-MM-DD\" (if they mention a payment date, otherwise null),\n" +
            "  \"confidence\": 0.95,\n" +
            "  \"reply\": \"A short, friendly 1-sentence reply to send back to the user.\"\n" +
            "}"
        ).build();
        this.customerRepository = customerRepository;
        this.promiseToPayRepository = promiseToPayRepository;
        this.notificationService = notificationService;
        this.chatMemory = chatMemory;
        this.objectMapper = new ObjectMapper();
    }

    public void processIncomingMessage(String phoneNumber, String rawMessage) {
        log.info("Processing incoming WhatsApp message from {}", phoneNumber);

        Customer customer = findCustomerByPhone(phoneNumber);
        if (customer == null) {
            log.warn("No customer found for phone number: {}", phoneNumber);
            return;
        }

        try {
            // Fetch DB conversation history directly from auto-configured ChatMemory
            List<Message> historyMessages = chatMemory.get(phoneNumber);
            StringBuilder history = new StringBuilder();
            if (historyMessages != null) {
                for (Message msg : historyMessages) {
                    history.append(msg.toString()).append("\n");
                }
            }
            history.append("USER: ").append(rawMessage).append("\n");

            String jsonResponse = chatClient.prompt()
                    .user(u -> u.text("Conversation History:\n{history}\n\nBased on the above, process the latest message.")
                            .param("history", history.toString()))
                    .call()
                    .content();

            JsonNode responseNode = objectMapper.readTree(jsonResponse);
            
            String intent = responseNode.path("intent").asText();
            String dateStr = responseNode.path("date").asText(null);
            double confidence = responseNode.path("confidence").asDouble(0.0);
            String reply = responseNode.path("reply").asText("Thank you for your message, we have noted it.");

            if ("PROMISE_TO_PAY".equalsIgnoreCase(intent) && dateStr != null && !"null".equalsIgnoreCase(dateStr)) {
                PromiseToPay promise = new PromiseToPay();
                promise.setCustomer(customer);
                promise.setRawMessage(rawMessage);
                try {
                    promise.setExtractedPromiseDate(LocalDate.parse(dateStr));
                } catch (Exception e) {
                    log.warn("Failed to parse date: {}", dateStr);
                }
                promise.setExtractedConfidence(BigDecimal.valueOf(confidence));
                promise.setStatus(PromiseStatus.PENDING);
                promise.setCreatedAt(LocalDateTime.now());
                promiseToPayRepository.save(promise);
                log.info("Saved PromiseToPay for customer ID {}", customer.getId());
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
