package ai.revenue.recovery.entity;

import ai.revenue.recovery.entity.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private FlowType flowType;

    private Long entityId;
    private String entityType;
    private String decision;

    private String paymentOrderId;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    private String actionTaken;

    @Enumerated(EnumType.STRING)
    private AuditOutcome outcome;

    private Integer attemptNumber;
    private LocalDateTime createdAt;
}
