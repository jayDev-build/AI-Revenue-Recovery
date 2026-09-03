package ai.revenue.recovery.service;

import ai.revenue.recovery.AiTools.SubscriptionTools;
import ai.revenue.recovery.entity.Customer;
import ai.revenue.recovery.entity.PromiseConfirmationState;
import ai.revenue.recovery.entity.PromiseToPay;
import ai.revenue.recovery.entity.Responses.LlmExtractionResult;
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
import java.util.Set;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class WhatsappInteractionService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappInteractionService.class);

    // Flexible affirmative/negative keyword sets for confirmation replies
    private static final Set<String> AFFIRMATIVES = Set.of("YES", "Y", "HAAN", "CONFIRM", "OK");
    private static final Set<String> NEGATIVES = Set.of("NO", "N", "NAHI", "CANCEL");

    private static final String SYSTEM_PROMPT_TEMPLATE = 
        "You are an Autonomous Revenue Recovery Agent for a subscription service.\n" +
        "Your SOLE objective is to negotiate and secure a strict Promise to Pay (PTP) date for failed subscription renewals.\n\n" +
        "### CONTEXT:\n" +
        "- Customer Name: {customer_name}\n" +
        "- Outstanding Amount: ₹{subscription_amount}\n" +
        "- Current Date: {current_date}\n\n" +
        "### STRICT RULES & CONSTRAINTS (CRITICAL):\n" +
        "1. NO DISCOUNTS: You must recover the exact amount of ₹{subscription_amount}. Never accept or negotiate a partial payment.\n" +
        "2. NO SERVICE GUARANTEES: You DO NOT have the authority to grant or guarantee service access. If the user asks if their account will remain active, you MUST reply strictly with: \"Account access is managed by the billing system based on your grace period. My role is only to log your payment date.\" Never use the words \"your plan will remain active\" or \"maintained.\"\n" +
        "3. EXACT DATES ONLY: If a user gives a relative time (\"in 2 days\", \"next week\"), calculate the exact date based on {current_date}.\n" +
        "4. NO ENDLESS CHITCHAT: Once the user provides a clear date, confirm the date, state that the billing system will automatically retry the charge on that day, and END the negotiation. Do not answer redundant questions.\n" +
        "5. NO HALLUCINATION: Do not invent policies, consequences, or features.\n\n" +
        "### TONE:\n" +
        "Professional, firm, and transactional. You are not a friend; you are a financial system interface.\n\n" +
        "### REQUIRED OUTPUT (JSON extraction via tools):\n" +
        "When a valid date is reached, extract the absolute date and the exact amount.\n" +
        "Your FINAL text response must be ONLY a raw JSON object with the following structure (no markdown):\n" +
        "{\n" +
        "  \"intent\": \"PROMISE_TO_PAY\" | \"ALREADY_PAID\" | \"NEED_MORE_TIME\" | \"CANCEL_SUBSCRIPTION\" | \"OTHER\",\n" +
        "  \"date\": \"YYYY-MM-DDTHH:MM:SS\" (if they mention a payment date or time, calculate the exact future date and time based on Current Date, otherwise null),\n" +
        "  \"amount\": null (if they mention a specific payment amount, extract it as a number, otherwise null),\n" +
        "  \"confidence\": 0.95,\n" +
        "  \"reply\": \"A short, firm, and transactional 1-sentence reply to send back to the user.\"\n" +
        "}";

    private final ChatClient chatClient;
    private final CustomerRepository customerRepository;
    private final PromiseToPayRepository promiseToPayRepository;
    private final WhatsAppNotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final ChatMemory chatMemory;
    private final SubscriptionTools subscriptionTools;
    private final PromiseValidationService promiseValidationService;

    public WhatsappInteractionService(ChatClient.Builder chatClientBuilder,
                                      CustomerRepository customerRepository,
                                      PromiseToPayRepository promiseToPayRepository,
                                      WhatsAppNotificationService notificationService,
                                      SubscriptionTools subscriptionTools,
                                      ChatMemory chatMemory,
                                      PromiseValidationService promiseValidationService) {
        this.chatClient = chatClientBuilder.build();
        this.customerRepository = customerRepository;
        this.promiseToPayRepository = promiseToPayRepository;
        this.notificationService = notificationService;
        this.chatMemory = chatMemory;
        this.subscriptionTools = subscriptionTools;
        this.promiseValidationService = promiseValidationService;
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

        // ── CONFIRMATION GATE ──────────────────────────────────────────
        // Check if this customer has an active AWAITING_PROMISE_CONFIRMATION state.
        // If so, handle YES/NO directly without involving the LLM.
        PromiseConfirmationState activeState = promiseValidationService.getActiveConfirmationState(phoneNumber);
        if (activeState != null) {
            String normalized = rawMessage.trim().toUpperCase();

            if (AFFIRMATIVES.contains(normalized)) {
                // Customer confirmed — promote promise to PENDING
                try {
                    promiseValidationService.confirmPendingPromise(phoneNumber);
                    String reply = "✅ Your payment promise has been confirmed! We'll remind you on the scheduled date.";
                    chatMemory.add(phoneNumber, List.of(new UserMessage(rawMessage), new AssistantMessage(reply)));
                    notificationService.sendTextMessage(phoneNumber, reply);
                } catch (Exception e) {
                    log.error("Failed to confirm promise for phone {}: {}", phoneNumber, e.getMessage());
                    notificationService.sendTextMessage(phoneNumber,
                            "Sorry, we encountered an issue confirming your promise. Please try again.");
                }
                return; // Skip LLM entirely

            } else if (NEGATIVES.contains(normalized)) {
                // Customer rejected — cancel the pending promise
                try {
                    promiseValidationService.rejectPendingPromise(phoneNumber);
                    String reply = "❌ Your payment promise has been cancelled. Feel free to reach out if you'd like to reschedule.";
                    chatMemory.add(phoneNumber, List.of(new UserMessage(rawMessage), new AssistantMessage(reply)));
                    notificationService.sendTextMessage(phoneNumber, reply);
                } catch (Exception e) {
                    log.error("Failed to reject promise for phone {}: {}", phoneNumber, e.getMessage());
                }
                return; // Skip LLM entirely

            } else {
                // Unrecognized reply — re-prompt without touching the LLM
                String reply = "I didn't understand your reply. Please reply YES to confirm your payment promise or NO to cancel.";
                chatMemory.add(phoneNumber, List.of(new UserMessage(rawMessage), new AssistantMessage(reply)));
                notificationService.sendTextMessage(phoneNumber, reply);
                return; // Skip LLM entirely
            }
        }

        // Check for expired state — clean up and fall through to LLM
        promiseValidationService.deleteExpiredConfirmationState(phoneNumber);

        // ── EXISTING LLM FLOW ──────────────────────────────────────────
        try {
            BigDecimal subAmount = BigDecimal.ZERO;
            if (customer.getSubscriptions() != null && !customer.getSubscriptions().isEmpty()) {
                ai.revenue.recovery.entity.Subscription linkedSub = customer.getSubscriptions().stream()
                    .filter(s -> ai.revenue.recovery.entity.enums.SubscriptionStatus.PAST_DUE.equals(s.getStatus()) ||
                                 ai.revenue.recovery.entity.enums.SubscriptionStatus.CANCELLED.equals(s.getStatus()))
                    .findFirst()
                    .orElse(customer.getSubscriptions().get(0));
                if (linkedSub != null && linkedSub.getPlanAmount() != null) {
                    subAmount = linkedSub.getPlanAmount();
                }
            }
            final String finalSubAmount = subAmount.toString();
            final String finalCustomerName = customer.getName() != null ? customer.getName() : "Customer";

            // Fetch DB conversation history directly from auto-configured ChatMemory
            List<Message> historyMessages = new java.util.ArrayList<>(chatMemory.get(phoneNumber));
            
            Message newUserMessage = new UserMessage(rawMessage);
            historyMessages.add(newUserMessage);

            String finalSystemPrompt = SYSTEM_PROMPT_TEMPLATE
                    .replace("{customer_name}", finalCustomerName)
                    .replace("{subscription_amount}", finalSubAmount)
                    .replace("{current_date}", LocalDateTime.now().toString());

            String jsonResponse = chatClient.prompt()
                    .system(finalSystemPrompt)
                    .messages(historyMessages)
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

            // Extract amount if provided by LLM
            BigDecimal extractedAmount = null;
            if (responseNode.has("amount") && !responseNode.path("amount").isNull()) {
                try {
                    extractedAmount = new BigDecimal(responseNode.path("amount").asText());
                } catch (Exception e) {
                    log.debug("Could not parse amount from LLM response");
                }
            }

            if ("PROMISE_TO_PAY".equalsIgnoreCase(intent) && dateStr != null && !"null".equalsIgnoreCase(dateStr)) {
                // ── GUARDRAIL PIPELINE ──────────────────────────────────
                // Route through deterministic validation instead of saving directly
                LlmExtractionResult extraction = LlmExtractionResult.builder()
                        .intent(intent)
                        .extractedDate(dateStr)
                        .extractedAmount(extractedAmount)
                        .confidenceScore(confidence)
                        .reply(reply)
                        .build();

                PromiseToPay promise = promiseValidationService.validateAndCreatePromise(
                        customer, extraction, rawMessage, phoneNumber);

                if (promise.getStatus() == PromiseStatus.NEEDS_HUMAN_REVIEW) {
                    // Override the LLM reply with a deterministic confirmation prompt
                    String promiseDate = promise.getExtractedPromiseDate() != null
                            ? promise.getExtractedPromiseDate().toString() : "the date you mentioned";
                    String promiseAmount = promise.getExtractedAmount() != null
                            ? "₹" + promise.getExtractedAmount() : "the amount discussed";
                    reply = "We noted your intent to pay " + promiseAmount + " on " + promiseDate +
                            ". Reply YES to confirm or NO to cancel.";
                }

                log.info("Saved PromiseToPay (via guardrail) for customer ID {} with status {}",
                        customer.getId(), promise.getStatus());

            } else if ("ALREADY_PAID".equalsIgnoreCase(intent)) {
                List<PromiseToPay> pendingPromises = promiseToPayRepository.findByCustomerId(customer.getId())
                        .stream().filter(p -> p.getStatus() == PromiseStatus.PENDING
                                || p.getStatus() == PromiseStatus.CONFIRMED
                                || p.getStatus() == PromiseStatus.NEEDS_HUMAN_REVIEW).toList();
                
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
