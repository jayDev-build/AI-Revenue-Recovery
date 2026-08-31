package ai.revenue.recovery.Whatsapp;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WhatsAppTemplate {

    @JsonProperty("messaging_product")
    private String messagingProduct = "whatsapp";

    @JsonProperty("recipient_type")
    private String recipientType = "individual";

    private String to;
    private String type = "template";
    private Template template;

    public WhatsAppTemplate(String to, String templateName, String languageCode, List<String> bodyValues) {
        // 1. Clean and format the phone number
        String cleanTo = to.replaceAll("[^0-9]", "");
        if (cleanTo.length() == 10) {
            this.to = "91" + cleanTo;
        } else {
            this.to = cleanTo;
        }

        // 2. Map string values to WhatsApp Parameters (using our static inner class)
        List<Parameter> bodyComponents = bodyValues.stream()
                .map(v -> {
                    if (v == null) {
                        return new Parameter("text", "");
                    }

                    // Replace newlines and tabs with a standard single space
                    String sanitized = v.replaceAll("[\\n\\r\\t]", " ");

                    // Replace 4 or more consecutive spaces with a single space
                    sanitized = sanitized.replaceAll(" {4,}", " ");

                    return new Parameter("text", sanitized.trim());
                })
                .toList();

        // 3. Create the body component
        Component bodyComponent = new Component("body", bodyComponents);

        // 4. Initialize the template object
        this.template = new Template(templateName, new Language(languageCode), List.of(bodyComponent));
    }

    @Data
    public static class Template {
        private String name;
        private Language language;
        private List<Component> components;

        public Template(String templateName, Language language, List<Component> components) {
            this.name = templateName;
            this.language = language;
            this.components = components;
        }
    }

    @Data
    public static class Language {
        private String code;

        public Language(String code) {
            this.code = code;
        }
    }

    @Data
    public static class Component {
        private String type;
        private List<Parameter> parameters;

        public Component(String type, List<Parameter> parameters) {
            this.type = type;
            this.parameters = parameters;
        }
    }

    @Data
    public static class Parameter {
        private String type;
        private String text;

        public Parameter(String type, String text) {
            this.type = type;
            this.text = text;
        }
    }
}