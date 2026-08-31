package ai.revenue.recovery.Whatsapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class WhatsAppNotificationService {

    private final RestTemplate restTemplate = new RestTemplate();

    // --- 5. Replace these placeholders with your actual Meta credentials ---
    @Value("${whatsapp.access-token}")
    private String accessToken; // "YOUR_TEMPORARY_ACCESS_TOKEN"

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId; // "YOUR_PHONE_NUMBER_ID"

    @Value("${whatsapp.graph-api-version}")
    private String graphApiVersion; // "v20.0"

    public void sendTemplateNotification(String toPhoneNumber, String templateName, String languageCode,
                                         java.util.List<String> bodyValues) {
        // 1. Prepare the Request Body
        // We use our WhatsAppTemplate class to structure the JSON payload
        WhatsAppTemplate requestBody = new WhatsAppTemplate(toPhoneNumber, templateName, languageCode, bodyValues);

        // 2. Setup Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Bearer token authentication
        headers.setBearerAuth(accessToken);

        // 3. Make the Request to Meta's Graph API
        String url = "https://graph.facebook.com/v25.0" + "/" + phoneNumberId + "/messages";

        HttpEntity<WhatsAppTemplate> entity = new HttpEntity<>(requestBody, headers);

        try {
            // Send POST request
            String response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class).getBody();

            System.out.println("WhatsApp Notification Sent Successfully! Response: " + response);
        } catch (Exception e) {
            // Log the error instead of just printing the stack trace
            System.err.println("Error sending WhatsApp message: " + e.getMessage());
            throw new RuntimeException("Failed to send WhatsApp notification", e);
        }
    }

    public void sendTextMessage(String toPhoneNumber, String messageContent) {
        String cleanTo = toPhoneNumber.replaceAll("[^0-9]", "");
        if (cleanTo.length() == 10) {
            cleanTo = "91" + cleanTo;
        }

        java.util.Map<String, Object> textObject = java.util.Map.of(
                "preview_url", false,
                "body", messageContent
        );
        java.util.Map<String, Object> payload = java.util.Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", cleanTo,
                "type", "text",
                "text", textObject
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        String url = "https://graph.facebook.com/v25.0/" + phoneNumberId + "/messages";
        HttpEntity<java.util.Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            String response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class).getBody();
            System.out.println("WhatsApp Text Message Sent Successfully! Response: " + response);
        } catch (Exception e) {
            System.err.println("Error sending WhatsApp text: " + e.getMessage());
        }
    }
}
