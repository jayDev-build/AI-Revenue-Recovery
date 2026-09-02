package ai.revenue.recovery.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String phoneNumber;
    private String email;
    private String password;
    private LocalDateTime createdAt;
    private BigDecimal recovered;

    @Builder.Default
    private Integer brokenPromisesCount = 0;

    @OneToMany(mappedBy = "customer")
    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Subscription> subscriptions = new ArrayList<>();
}
