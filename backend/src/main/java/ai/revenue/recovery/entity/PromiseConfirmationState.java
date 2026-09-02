package ai.revenue.recovery.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "promise_confirmation_state")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromiseConfirmationState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String phoneNumber;

    private Long pendingPromiseId;

    private String status;

    private LocalDateTime createdAt;
}
