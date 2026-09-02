package ai.revenue.recovery.entity.Responses;

import lombok.*;

import java.math.BigDecimal;

/**
 * Structured DTO for LLM parameter extraction output.
 * The LLM's raw JSON response is parsed into this DTO,
 * then validated by PromiseValidationService before
 * any data is written to the PromiseToPay table.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmExtractionResult {
    private String intent;
    private String extractedDate;
    private BigDecimal extractedAmount;
    private double confidenceScore;
    private String reply;
}
