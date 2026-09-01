package ai.revenue.recovery.controller;

import ai.revenue.recovery.service.WhatsappInteractionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/whatsapp/webhook")
public class WhatsappWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsappWebhookController.class);

    // @Value("${whatsapp.verify-token:my_secure_verify_token}")
    private String verifyToken = "ai_revenue_recovery_verify_token";

    private final WhatsappInteractionService interactionService;

    public WhatsappWebhookController(WhatsappInteractionService interactionService) {
        this.interactionService = interactionService;
    }

    /**
     * Required by Meta for Webhook verification.
     */
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        log.info("Received WhatsApp webhook verification request.");
        
        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("Webhook verified successfully!");
            return ResponseEntity.ok(challenge);
        } else {
            log.warn("Webhook verification failed!");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Receives incoming messages from WhatsApp.
     */
    @PostMapping
    public ResponseEntity<String> receiveMessage(@RequestBody String payloadStr) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode payload = mapper.readTree(payloadStr);
            if (payload.has("object") && "whatsapp_business_account".equals(payload.get("object").asText())) {
                JsonNode entries = payload.path("entry");
                for (JsonNode entry : entries) {
                    JsonNode changes = entry.path("changes");
                    for (JsonNode change : changes) {
                        JsonNode value = change.path("value");
                        JsonNode messages = value.path("messages");

                        if (messages != null && messages.isArray() && messages.size() > 0) {
                            JsonNode message = messages.get(0);
                            String from = message.path("from").asText();
                            
                            String textBody = null;
                            if (message.has("text")) {
                                textBody = message.path("text").path("body").asText();
                            } else if (message.has("interactive")) {
                                JsonNode interactive = message.path("interactive");
                                if (interactive.has("button_reply")) {
                                    textBody = interactive.path("button_reply").path("title").asText();
                                } else if (interactive.has("list_reply")) {
                                    textBody = interactive.path("list_reply").path("title").asText();
                                }
                            }

                            if (textBody != null) {
                                final String finalBody = textBody;
                                // Process asynchronously to immediately return 200 OK to Meta and prevent retries
                                java.util.concurrent.CompletableFuture.runAsync(() -> {
                                    interactionService.processIncomingMessage(from, finalBody);
                                });
                            }
                        }
                    }
                }
            }
            return ResponseEntity.ok("EVENT_RECEIVED");
        } catch (Exception e) {
            log.error("Error processing webhook payload: ", e);
            // Return 200 so Meta doesn't retry indefinitely
            return ResponseEntity.ok("EVENT_RECEIVED_WITH_ERROR");
        }
    }
}
